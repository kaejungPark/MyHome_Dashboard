package com.myhome.email.signup;

import com.myhome.auth.AuthMapper;
import com.myhome.common.mail.MailService;
import com.myhome.email.EmailVerificationCodeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 회원가입 전 이메일 소유 확인과 일회용 가입 확인값 발급을 담당한다.
 *
 * 이메일로 받은 인증번호를 확인하면 가입 확인값을 발급하고,
 * 실제 회원가입 시에는 이 확인값의 이메일·유효기간·사용 여부를 다시 검사한다.
 *
 * 번호 발급·확인은 별도 트랜잭션으로 처리하고,
 * 가입 확인값 사용 처리는 회원가입 트랜잭션에 참여한다.
 * 실제 메일 전송은 DB 트랜잭션 밖에서 수행한다.
 */
@Service
public class SignupEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(SignupEmailService.class);

    // 이메일로 받은 인증번호를 입력할 수 있는 시간
    private static final int CODE_VALID_MINUTES = 5;

    // 인증 성공 후 일회용 확인값으로 회원가입을 완료할 수 있는 시간
    private static final int PROOF_VALID_MINUTES = 15;

    // 같은 이메일에 인증번호를 다시 발급할 수 있는 최소 간격
    private static final int RESEND_INTERVAL_SECONDS = 60;

    // 같은 이메일의 최근 1시간 발급 제한. 실패·무효화된 요청도 포함한다.
    private static final int MAX_ISSUES_PER_HOUR = 5;

    // 한 번 발급한 인증번호에 허용하는 오입력 횟수
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SignupEmailMapper signupEmailMapper;
    private final AuthMapper authMapper;
    private final EmailVerificationCodeSupport codeSupport;
    private final MailService mailService;

    // 인증번호 발급·확인·발송 실패 정리에 사용하는 트랜잭션 도구다.
    // consumeForSignup()은 이 도구를 사용하지 않고 회원가입 트랜잭션에 참여한다.
    private final TransactionTemplate transactions;

    public SignupEmailService(
            SignupEmailMapper signupEmailMapper,
            AuthMapper authMapper,
            EmailVerificationCodeSupport codeSupport,
            MailService mailService,
            PlatformTransactionManager transactionManager
    ) {
        this.signupEmailMapper = signupEmailMapper;
        this.authMapper = authMapper;
        this.codeSupport = codeSupport;
        this.mailService = mailService;

        this.transactions = new TransactionTemplate(transactionManager);

        // 발급·확인 결과를 별도 트랜잭션에서 먼저 확정한다.
        this.transactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * 인증 요청을 저장한 뒤 트랜잭션 밖에서 메일을 발송한다.
     * 인증번호 원문은 메일에만 포함하고 API 응답에는 넣지 않는다.
     */
    public SignupEmailSendResponse sendVerification(
            SignupEmailSendRequest request
    ) {
        String email = normalizeEmail(request.email());

        // prepareVerification은 이 콜백 안에서 실행되므로
        // 메서드가 반환될 때 발급 이력 저장과 잠금 해제가 완료된다.
        Prepared prepared = Objects.requireNonNull(
                transactions.execute(
                        status -> prepareVerification(email)
                )
        );

        String content = """
                안녕하세요. MyHome입니다.

                회원가입 이메일 인증번호를 안내드립니다.

                인증번호: %s

                회원가입 화면에서 위 번호를 입력해 주세요.
                인증번호는 발급 시점부터 5분 동안 사용할 수 있습니다.
                재발송했다면 가장 최근에 받은 번호를 입력해 주세요.

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(prepared.code());

        try {
            mailService.send(
                    email,
                    "[MyHome] 회원가입 이메일 인증번호",
                    content
            );
        } catch (MailException | IllegalArgumentException exception) {
            // 인증번호·메일 본문·예외에 포함된 계정 정보는 출력하지 않는다.
            log.warn(
                    "가입 인증 메일 발송 실패: requestId={}, exceptionType={}",
                    prepared.requestId(),
                    exception.getClass().getSimpleName()
            );

            revokeAfterSendFailure(email, prepared.requestId());

            throw failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "인증 메일을 발송하지 못했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return new SignupEmailSendResponse(
                prepared.requestId(),
                prepared.expiresDt().toInstant(ZoneOffset.UTC),
                prepared.createdDt()
                        .plusSeconds(RESEND_INTERVAL_SECONDS)
                        .toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * 인증번호를 검사하고 성공하면 일회용 가입 확인값을 반환한다.
     * 실패 횟수를 먼저 커밋한 뒤 오류를 발생시켜 횟수가 롤백되지 않게 한다.
     */
    public SignupEmailConfirmResponse confirmVerification(
            SignupEmailConfirmRequest request
    ) {
        String email = normalizeEmail(request.email());

        ConfirmOutcome outcome = Objects.requireNonNull(
                transactions.execute(
                        status -> verifyCode(email, request)
                )
        );

        // execute가 끝난 뒤이므로 오입력 횟수는 이미 저장된 상태다.
        if (outcome.errorStatus() != null) {
            throw failure(
                    outcome.errorStatus(),
                    outcome.errorMessage()
            );
        }

        return outcome.response();
    }

    /**
     * 이메일과 일회용 가입 확인값을 검증하고 사용 완료 처리한다.
     *
     * MANDATORY는 새 트랜잭션을 만들지 않고 호출자의 트랜잭션 참여를 강제한다.
     * 회원 INSERT가 실패하면 확인값 사용 처리도 함께 롤백되어 재시도할 수 있다.
     *
     * 다른 빈의 @Transactional 회원가입 메서드에서 호출해야 한다.
     *
     * @param email 회원가입에 사용할 이메일
     * @param verificationToken 인증번호 확인 성공 시 발급받은 원본 확인값
     * @return USER.EMAIL_VERIFIED_DT에 저장할 실제 인증 완료 시각
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDateTime consumeForSignup(
            String email,
            String verificationToken
    ) {
        String normalizedEmail = normalizeEmail(email);

        if (verificationToken == null
                || !verificationToken.matches("[A-Za-z0-9_-]{43}")) {
            throw failure(
                    HttpStatus.BAD_REQUEST,
                    "이메일 인증을 먼저 완료해 주세요."
            );
        }

        lockEmail(normalizedEmail);

        String proofHash =
                codeSupport.hashVerificationToken(verificationToken);

        int updatedRows = signupEmailMapper.consume(
                normalizedEmail,
                proofHash,
                nowUtc()
        );

        // 이메일 불일치·만료·재사용·무효화된 확인값은 모두 거절한다.
        if (updatedRows != 1) {
            throw failure(
                    HttpStatus.BAD_REQUEST,
                    "사용할 수 없는 이메일 인증 정보입니다. 다시 인증해 주세요."
            );
        }

        // 회원가입 시 USER.EMAIL_VERIFIED_DT에 저장할 실제 인증 시각이다.
        return Objects.requireNonNull(
                signupEmailMapper.findVerifiedDt(
                        normalizedEmail,
                        proofHash
                )
        );
    }

    /**
     * 이메일의 앞뒤 공백과 대소문자를 통일한다.
     * 회원가입 시에도 동일한 정규화 규칙을 사용해야 한다.
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw failure(
                    HttpStatus.BAD_REQUEST,
                    "이메일을 입력해 주세요."
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 발급 제한을 검사하고 새 인증번호의 해시를 저장한다.
     * transactions.execute 내부에서만 호출한다.
     */
    private Prepared prepareVerification(String email) {
        // 같은 이메일에 대한 동시 발급 요청을 순서대로 처리한다.
        lockEmail(email);

        if (authMapper.existsByEmail(email)) {
            throw failure(
                    HttpStatus.CONFLICT,
                    "이미 가입된 이메일입니다."
            );
        }

        LocalDateTime now = nowUtc();

        LocalDateTime latestCreatedDt =
                signupEmailMapper.findLatestCreated(email);

        if (latestCreatedDt != null
                && now.isBefore(
                latestCreatedDt.plusSeconds(RESEND_INTERVAL_SECONDS)
        )) {
            throw failure(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호는 발급 후 1분이 지나면 다시 요청할 수 있습니다."
            );
        }

        int recentCount = signupEmailMapper.countRecent(
                email,
                now.minusHours(1)
        );

        if (recentCount >= MAX_ISSUES_PER_HOUR) {
            throw failure(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "이메일당 1시간에 최대 5회 요청할 수 있습니다."
            );
        }

        UUID requestId = codeSupport.generateRequestId();
        String code = codeSupport.generateCode();

        String codeHash = codeSupport.createSignupHash(
                email,
                requestId,
                code
        );

        LocalDateTime expiresDt = now.plusMinutes(CODE_VALID_MINUTES);

        // 새로 발급하면 이전 번호와 이전 가입 확인값은 무효화한다.
        signupEmailMapper.revokePrevious(email, now);

        requireOne(signupEmailMapper.insert(
                requestId.toString(),
                email,
                codeHash,
                expiresDt,
                now
        ));

        return new Prepared(
                requestId,
                code,
                now,
                expiresDt
        );
    }

    /**
     * 인증번호를 검사하고 DB 상태를 변경한다.
     * 예상 가능한 인증 실패는 예외 대신 결과로 반환한다.
     */
    private ConfirmOutcome verifyCode(
            String email,
            SignupEmailConfirmRequest request
    ) {
        lockEmail(email);

        SignupEmailToken token = signupEmailMapper.findForUpdate(
                request.requestId().toString(),
                email
        );

        LocalDateTime now = nowUtc();

        if (token == null) {
            return ConfirmOutcome.failure(
                    HttpStatus.BAD_REQUEST,
                    "인증 요청을 찾을 수 없습니다. 인증번호를 다시 요청해 주세요."
            );
        }

        if (token.usedDt() != null || token.revokedDt() != null) {
            return ConfirmOutcome.failure(
                    HttpStatus.CONFLICT,
                    "사용할 수 없는 인증 요청입니다. 다시 요청해 주세요."
            );
        }

        // 이미 인증한 요청에서는 가입 확인값을 다시 발급하지 않는다.
        // DB에는 확인값의 해시만 있으므로 이전 원본을 복원할 수 없다.
        // 클라이언트가 확인값을 잃었다면 인증번호를 새로 발급받아야 한다.
        if (token.verifiedDt() != null) {
            return ConfirmOutcome.failure(
                    HttpStatus.CONFLICT,
                    "이미 인증을 완료한 요청입니다. 가입 확인값을 잃었다면 다시 인증해 주세요."
            );
        }

        if (token.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
            return ConfirmOutcome.failure(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "입력 가능 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."
            );
        }

        if (!now.isBefore(token.expiresDt())) {
            return ConfirmOutcome.failure(
                    HttpStatus.GONE,
                    "인증번호가 만료됐습니다. 다시 요청해 주세요."
            );
        }

        boolean matches = codeSupport.matchesSignupCode(
                email,
                request.requestId(),
                request.code(),
                token.codeHash()
        );

        // 번호 불일치는 실패 횟수 증가 후 결과 객체로 반환한다.
        // 여기서 예외를 던지면 트랜잭션이 롤백되어 실패 횟수도 취소될 수 있다.
        // HTTP 오류는 confirmVerification()에서 커밋이 끝난 뒤 발생시킨다.
        if (!matches) {
            requireOne(
                    signupEmailMapper.increaseFailures(token.id())
            );

            int remainingAttempts =
                    MAX_FAILED_ATTEMPTS - token.failedAttempts() - 1;

            if (remainingAttempts == 0) {
                return ConfirmOutcome.failure(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "입력 가능 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."
                );
            }

            return ConfirmOutcome.failure(
                    HttpStatus.BAD_REQUEST,
                    "인증번호가 일치하지 않습니다. 남은 입력 횟수: "
                            + remainingAttempts + "회"
            );
        }

        // 인증번호와 별개의 긴 난수 확인값을 발급한다.
        String verificationToken =
                codeSupport.generateVerificationToken();

        String proofHash =
                codeSupport.hashVerificationToken(verificationToken);

        LocalDateTime proofExpiresDt =
                now.plusMinutes(PROOF_VALID_MINUTES);

        requireOne(signupEmailMapper.markVerified(
                token.id(),
                proofHash,
                proofExpiresDt,
                now
        ));

        return ConfirmOutcome.success(
                new SignupEmailConfirmResponse(
                        verificationToken,
                        proofExpiresDt.toInstant(ZoneOffset.UTC)
                )
        );
    }

    /**
     * 메일 발송 실패 시 해당 요청을 별도 트랜잭션에서 무효화한다.
     */
    private void revokeAfterSendFailure(
            String email,
            UUID requestId
    ) {
        try {
            transactions.executeWithoutResult(status -> {
                lockEmail(email);

                signupEmailMapper.revokeFailedSend(
                        requestId.toString(),
                        nowUtc()
                );
            });
        } catch (RuntimeException exception) {
            log.error(
                    "발송 실패 인증 요청 정리 실패: requestId={}, exceptionType={}",
                    requestId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 같은 이메일의 인증 요청을 순서대로 처리한다.
     * 잠금 실패 시 반환 코드를 기록해 원인을 구분한다.
     */
    private void lockEmail(String email) {
        String resource = "signup-email:" + codeSupport.sha256(email);

        int result = signupEmailMapper.lockEmail(resource);

        if (result >= 0) {
            return;
        }

        // 이메일·인증번호·가입 확인값은 로그에 남기지 않는다.
        log.error("가입 전 이메일 인증 DB 잠금 실패: result={}", result);

        if (result == -1) {
            throw failure(
                    HttpStatus.CONFLICT,
                    "인증 요청 대기 시간이 초과됐습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        if (result == -2 || result == -3) {
            throw failure(
                    HttpStatus.CONFLICT,
                    "인증 요청 처리가 중단됐습니다. 다시 시도해 주세요."
            );
        }

        throw failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "이메일 인증 준비 중 오류가 발생했습니다."
        );
    }

    /**
     * 인증 이력의 저장·만료 비교에 사용할 UTC 현재 시각을 반환한다.
     * DB의 DATETIME2에는 시간대 정보가 없으므로 UTC 기준을 일관되게 적용한다.
     */
    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 단일 인증 이력의 변경 결과가 정확히 1행인지 확인한다.
     * 예상과 다르면 예외를 발생시켜 현재 트랜잭션을 롤백한다.
     */
    private static void requireOne(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "이메일 인증 이력을 정상적으로 저장하지 못했습니다."
            );
        }
    }

    private static ResponseStatusException failure(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }

    /**
     * 발급 트랜잭션에서 메일 발송 단계로 전달하는 내부 데이터다.
     * 인증번호를 포함하므로 로그에 출력하지 않는다.
     */
    private record Prepared(
            UUID requestId,
            String code,
            LocalDateTime createdDt,
            LocalDateTime expiresDt
    ) {
        @Override
        public String toString() {
            return "Prepared[redacted]";
        }
    }

    /**
     * 인증 실패 횟수를 커밋한 후 HTTP 오류로 변환하기 위한 내부 결과다.
     */
    private record ConfirmOutcome(
            SignupEmailConfirmResponse response,
            HttpStatus errorStatus,
            String errorMessage
    ) {
        static ConfirmOutcome success(
                SignupEmailConfirmResponse response
        ) {
            return new ConfirmOutcome(response, null, null);
        }

        static ConfirmOutcome failure(
                HttpStatus status,
                String message
        ) {
            return new ConfirmOutcome(null, status, message);
        }

        @Override
        public String toString() {
            return "ConfirmOutcome[redacted]";
        }
    }
}