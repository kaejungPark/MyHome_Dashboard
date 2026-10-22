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
 * 환경변수의 Base64 비밀키를 읽어 HMAC 키를 구성한다.
 * 형식이 잘못됐거나 키가 32바이트 미만이면 애플리케이션 시작을 중단한다.
 * 기존 인증번호를 검증할 수 있도록 재시작 후에도 같은 키를 사용한다.
 */
@Component
public class EmailVerificationCodeSupport {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // 일반 Random 대신 암호학적으로 안전한 난수 생성기를 사용한다.
    private final SecureRandom secureRandom = new SecureRandom();

    // 환경변수에서 읽은 서버 전용 비밀키다.
    private final SecretKeySpec secretKey;

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
}
