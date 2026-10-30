package com.myhome.email;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 이미 가입한 회원의 인증번호 발급·확인에 필요한 DB 작업을 처리한다.
 * 메일 전송은 포함하지 않고 인증 이력과 회원의 인증 상태만 관리한다.
 *
 * 인증번호 불일치는 결과로 반환하여 오입력 횟수를 먼저 저장한다.
 * 오류를 바로 던져 실패 횟수 증가까지 취소되는 상황을 피하기 위한 구조다.
 */
@Service
public class EmailVerificationTransactionService {

    private static final int CODE_VALID_MINUTES = 5;         // 인증번호 유효기간
    private static final int RESEND_INTERVAL_SECONDS = 60;  // 재발송 최소 간격
    private static final int MAX_ISSUES_PER_HOUR = 5;       // 최근 1시간 최대 발급 횟수
    private static final int MAX_FAILED_ATTEMPTS = 5;       // 발급 건별 최대 입력 실패 횟수

    private final EmailTokenMapper emailTokenMapper;
    private final EmailVerificationCodeSupport codeSupport;

    public EmailVerificationTransactionService(
            EmailTokenMapper emailTokenMapper,
            EmailVerificationCodeSupport codeSupport
    ) {
        this.emailTokenMapper = emailTokenMapper;
        this.codeSupport = codeSupport;
    }

    /**
     * 발송 제한을 검사하고 인증번호 발급 정보를 저장한다.
     * 실제 이메일 발송은 트랜잭션이 끝난 뒤 호출한 서비스에서 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailVerificationPrepared prepareVerification(Long userId) {
        EmailVerificationUser user = requireActiveUser(userId);

        if (user.emailVerifiedDt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 인증된 이메일입니다."
            );
        }

        // admin 같은 로그인 별칭에는 인증 메일을 보낼 수 없다.
        validateEmail(user.email());

        // 잠금을 획득한 뒤 시각을 계산한다.
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        LocalDateTime latestIssuedAt =
                emailTokenMapper.findLatestIssuedAt(userId);

        if (latestIssuedAt != null
                && nowUtc.isBefore(
                latestIssuedAt.plusSeconds(RESEND_INTERVAL_SECONDS)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호는 마지막 발송 요청으로부터 1분 후 다시 요청할 수 있습니다."
            );
        }

        long recentIssues = emailTokenMapper.countRecentIssues(
                userId,
                nowUtc.minusHours(1)
        );

        if (recentIssues >= MAX_ISSUES_PER_HOUR) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호는 1시간에 최대 5회 요청할 수 있습니다."
            );
        }

        UUID requestId = codeSupport.generateRequestId();
        String code = codeSupport.generateCode();
        String tokenHash = codeSupport.createHash(userId, requestId, code);

        LocalDateTime expiresUtc =
                nowUtc.plusMinutes(CODE_VALID_MINUTES);

        LocalDateTime resendAvailableUtc =
                nowUtc.plusSeconds(RESEND_INTERVAL_SECONDS);

        // 새 토큰 저장에 실패하면 기존 번호 무효화도 함께 롤백된다.
        emailTokenMapper.revokeUnusedTokens(userId, nowUtc);

        int insertedRows = emailTokenMapper.insertToken(
                requestId.toString(),
                userId,
                user.email(),
                tokenHash,
                expiresUtc,
                nowUtc
        );

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "이메일 인증 토큰 저장에 실패했습니다."
            );
        }

        return new EmailVerificationPrepared(
                userId,
                requestId,
                user.email(),
                code,
                expiresUtc.toInstant(ZoneOffset.UTC),
                resendAvailableUtc.toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * 인증번호를 검사하고 결과를 저장한다.
     * 번호 불일치는 결과값으로 반환하여 실패 횟수 증가를 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailVerificationConfirmResult confirmVerification(
            Long userId,
            EmailVerificationConfirmRequest request
    ) {
        // 잠금 순서는 회원 → 토큰으로 통일한다.
        EmailVerificationUser user = requireActiveUser(userId);

        if (user.emailVerifiedDt() != null) {
            return result(
                    EmailVerificationConfirmResult.Status.ALREADY_VERIFIED
            );
        }

        if (request == null
                || request.requestId() == null
                || request.code() == null
                || !request.code().matches("[0-9]{6}")) {
            return result(
                    EmailVerificationConfirmResult.Status.INVALID_REQUEST
            );
        }

        EmailToken token = emailTokenMapper.findTokenForUpdate(
                userId,
                request.requestId().toString()
        );

        if (token == null) {
            return result(
                    EmailVerificationConfirmResult.Status.INVALID_REQUEST
            );
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        if (!user.email().equals(token.targetEmail())) {
            return result(
                    EmailVerificationConfirmResult.Status.EMAIL_CHANGED
            );
        }

        if (token.usedDt() != null) {
            return result(EmailVerificationConfirmResult.Status.USED);
        }

        if (token.failedAttemptCount() >= MAX_FAILED_ATTEMPTS) {
            return result(
                    EmailVerificationConfirmResult.Status.ATTEMPTS_EXCEEDED
            );
        }

        if (token.revokedDt() != null) {
            return result(EmailVerificationConfirmResult.Status.REVOKED);
        }

        if (!nowUtc.isBefore(token.expiresDt())) {
            return result(EmailVerificationConfirmResult.Status.EXPIRED);
        }

        boolean matched = codeSupport.matches(
                userId,
                request.requestId(),
                request.code(),
                token.tokenHash()
        );

        if (!matched) {
            int updatedRows = emailTokenMapper.increaseFailedAttempt(
                    token.id(),
                    userId,
                    MAX_FAILED_ATTEMPTS,
                    nowUtc
            );

            if (updatedRows != 1) {
                throw new IllegalStateException(
                        "인증번호 입력 실패 횟수를 저장하지 못했습니다."
                );
            }

            int remainingAttempts = Math.max(
                    0,
                    MAX_FAILED_ATTEMPTS - token.failedAttemptCount() - 1
            );

            if (remainingAttempts == 0) {
                return result(
                        EmailVerificationConfirmResult.Status.ATTEMPTS_EXCEEDED
                );
            }

            return new EmailVerificationConfirmResult(
                    EmailVerificationConfirmResult.Status.CODE_MISMATCH,
                    remainingAttempts
            );
        }

        // 토큰 사용과 회원 인증을 하나의 트랜잭션으로 처리한다.
        int usedRows = emailTokenMapper.markUsed(
                token.id(),
                userId,
                MAX_FAILED_ATTEMPTS,
                nowUtc
        );

        if (usedRows != 1) {
            throw new IllegalStateException(
                    "인증 토큰 사용 완료 처리에 실패했습니다."
            );
        }

        int verifiedRows = emailTokenMapper.markEmailVerified(
                userId,
                token.targetEmail(),
                nowUtc
        );

        if (verifiedRows != 1) {
            throw new IllegalStateException(
                    "회원 이메일 인증 완료 처리에 실패했습니다."
            );
        }

        return result(EmailVerificationConfirmResult.Status.SUCCESS);
    }

    /**
     * 메일 발송에 실패한 요청만 별도 트랜잭션으로 무효화한다.
     * 이미 사용·폐기된 경우 변경 행 수가 0이어도 정상이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAfterSendFailure(Long userId, UUID requestId) {
        EmailVerificationUser user =
                emailTokenMapper.findVerificationUser(userId);

        if (user == null) {
            return;
        }

        emailTokenMapper.revokeToken(
                userId,
                requestId.toString(),
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }

    /**
     * 현재 회원을 잠금 조회하고 활성 상태인지 확인한다.
     */
    private EmailVerificationUser requireActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 후 이용해 주세요."
            );
        }

        EmailVerificationUser user =
                emailTokenMapper.findVerificationUser(userId);

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "회원 정보를 찾을 수 없습니다."
            );
        }

        if (!"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 계정 상태에서는 이메일 인증을 진행할 수 없습니다."
            );
        }

        if (user.email() == null || user.email().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "인증할 이메일이 등록되어 있지 않습니다."
            );
        }

        return user;
    }

    // 실제 메일 주소 형식인지 확인한다. 주소의 소유 여부는 인증으로 확인한다.
    private void validateEmail(String email) {
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();

            if (!email.contains("@") || !email.equals(address.getAddress())) {
                throw new AddressException();
            }
        } catch (AddressException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "등록된 계정이 이메일 형식이 아닙니다. 이메일 주소를 확인해 주세요."
            );
        }
    }

    /**
     * 남은 입력 횟수를 안내할 필요가 없는 결과를 생성한다.
     * remainingAttempts의 0은 이 경우 입력 제한 초과를 의미하지 않는다.
     */
    private EmailVerificationConfirmResult result(
            EmailVerificationConfirmResult.Status status
    ) {
        return new EmailVerificationConfirmResult(status, 0);
    }
}