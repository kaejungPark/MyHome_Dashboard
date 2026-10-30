package com.myhome.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 이메일 인증에 필요한 난수 생성과 해시 비교를 담당한다.
 * 해시는 원본 인증번호 대신 저장하고 비교하는 값이다.
 *
 * 기존 회원 인증은 사용자 ID를 기준으로,
 * 가입 전 인증은 이메일을 기준으로 해시를 생성한다.
 * 인증번호 확인 후 회원가입에 사용할 별도의 일회용 확인값도 생성한다.
 */
@Component
public class EmailVerificationCodeSupport {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // 일반 Random 대신 암호학적으로 안전한 난수 생성기를 사용한다.
    private final SecureRandom secureRandom = new SecureRandom();

    // 환경변수에서 읽은 서버 전용 비밀키다.
    private final SecretKeySpec secretKey;

    /**
     * 환경변수에 저장한 Base64 비밀키를 읽어 인증번호 해시에 사용한다.
     * Base64는 키를 문자열로 보관하는 형식이며 암호화 방식은 아니다.
     * 키 형식이나 길이가 올바르지 않으면 초기화 단계에서 오류를 발생시킨다.
     */
    public EmailVerificationCodeSupport (
            @Value("${EMAIL_VERIFICATION_HMAC_KEY}") String encodedKey
    )
    {
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            // 예외 메시지에 실제 비밀키를 포함하지 않는다.
            throw new IllegalStateException(
                    "이메일 인증 HMAC 비밀키는 Base64 형식이어야 합니다."
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "이메일 인증 HMAC 비밀키는 최소 32바이트여야 합니다."
            );

        }

        this.secretKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);

    }

    /**
     * 000000~999999 범위의 인증번호를 생성한다.
     * 앞자리 0을 유지하기 위해 숫자가 아닌 문자열로 반환한다.
     */
    public String generateCode()
    {
        int number = secureRandom.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", number);
    }

    /**
     * 인증번호 발급 요청을 구분할 UUID를 생성한다.
     * 이 값 자체는 인증번호를 대신하지 않는다.
     */
    public UUID generateRequestId()
    {
        return UUID.randomUUID();
    }

    /**
     * 회원 ID·요청 ID·인증번호를 결합하여 HMAC을 계산한다.
     * 같은 인증번호라도 회원이나 발급 요청이 다르면 다른 해시가 된다.
     * DB 저장 형식에 맞춰 소문자 16진수 64자리로 반환한다.
     */
    public String  createHash(Long userId, UUID requestId, String code)
    {
        if (userId == null || userId <= 0 || requestId == null) {
            throw new IllegalArgumentException(
                    "인증번호 해시 생성에 필요한 정보가 올바르지 않습니다."
            );
        }

        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException(
                    "인증번호는 숫자 6자리여야 합니다."
            );
        }

        // 구분자를 사용해 각 값의 경계를 명확하게 한다.
        String payload = userId + ":" + requestId + ":" + code;

        try {
            // Mac은 스레드 간 공유하지 않고 호출마다 생성한다.
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);

            byte[] hashBytes = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "이메일 인증번호 해시를 생성하지 못했습니다.",
                    exception
            );
        }
    }

    /**
     * 입력한 인증번호의 HMAC과 DB에 저장된 HMAC을 비교한다.
     * 해시를 복호화하는 것이 아니라 같은 방법으로 다시 계산한다.
     */
    public boolean matches(
            Long userId,
            UUID requestId,
            String code,
            String storedHash
    ) {
        if (code == null || !code.matches("[0-9]{6}")) {
            return false;
        }

        if (storedHash == null || !storedHash.matches("[0-9a-f]{64}")) {
            return false;
        }

        String inputHash = createHash(userId, requestId, code);

        // 일반 문자열 비교 대신 해시 바이트 비교용 메서드를 사용한다.
        return MessageDigest.isEqual(
                HexFormat.of().parseHex(storedHash),
                HexFormat.of().parseHex(inputHash)
        );
    }

    /**
     * 가입 전 인증번호의 HMAC을 생성한다.
     * 아직 회원 ID가 없으므로 이메일·요청 ID·인증번호를 사용한다.
     */
    public String createSignupHash(
            String email,
            UUID requestId,
            String code
    ) {
        if (email == null || email.isBlank() || requestId == null) {
            throw new IllegalArgumentException(
                    "가입 전 인증 정보가 올바르지 않습니다."
            );
        }

        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException(
                    "인증번호는 숫자 6자리여야 합니다."
            );
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // 기존 회원 인증 해시와 구분하기 위해 용도를 함께 포함한다.
        String payload =
                "SIGNUP\n" + normalizedEmail + "\n" + requestId + "\n" + code;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);

            return HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "가입 전 인증번호 해시를 생성하지 못했습니다.",
                    exception
            );
        }
    }

    /**
     * 입력한 가입 전 인증번호와 저장된 해시가 일치하는지 확인한다.
     */
    public boolean matchesSignupCode(
            String email,
            UUID requestId,
            String code,
            String storedHash
    ) {
        if (code == null || !code.matches("[0-9]{6}")) {
            return false;
        }

        if (storedHash == null || !storedHash.matches("[0-9a-f]{64}")) {
            return false;
        }

        String inputHash = createSignupHash(email, requestId, code);

        return MessageDigest.isEqual(
                HexFormat.of().parseHex(storedHash),
                HexFormat.of().parseHex(inputHash)
        );
    }

    /**
     * 인증 성공 후 회원가입에 사용할 일회용 확인값을 생성한다.
     * 32바이트 난수를 URL 안전 Base64로 변환하면 43자리 문자열이 된다.
     */
    public String generateVerificationToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * 충분히 긴 난수로 생성한 가입 확인값을 SHA-256으로 해시 처리한다.
     * 원본 확인값은 클라이언트에 전달하고 DB에는 해시만 저장한다.
     *
     * 6자리 인증번호는 가능한 조합이 적어 서버 비밀키를 사용하는 HMAC으로 처리하지만,
     * 가입 확인값은 32바이트 난수이므로 별도의 SHA-256 해시로 저장한다.
     */
    public String hashVerificationToken(String verificationToken) {
        if (verificationToken == null
                || !verificationToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException(
                    "가입 인증 확인값이 올바르지 않습니다."
            );
        }

        return sha256(verificationToken);
    }

    /**
     * 문자열의 SHA-256 해시를 생성한다.
     * 가입 확인값 저장과 이메일별 잠금 이름 생성에 사용한다.
     */
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "SHA-256 해시를 생성하지 못했습니다.",
                    exception
            );
        }
    }
}
