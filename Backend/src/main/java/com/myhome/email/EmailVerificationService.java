package com.myhome.email;

import com.myhome.common.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 인증번호 DB 처리와 실제 메일 발송 흐름을 연결한다.
 * 메일 서버 응답을 기다리는 동안 DB 잠금을 유지하지 않도록
 * 이 클래스에는 트랜잭션을 적용하지 않는다.
 */
@Service
public class EmailVerificationService {

    private static final Logger log =
            LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationTransactionService transactionService;
    private final MailService mailService;

    public EmailVerificationService(
            EmailVerificationTransactionService transactionService,
            MailService mailService
    ) {
        this.transactionService = transactionService;
        this.mailService = mailService;
    }

    /**
     * 발급 정보를 DB에 저장한 뒤 인증 메일을 발송한다.
     * API 응답에는 원본 인증번호를 포함하지 않는다.
     */
    public EmailVerificationSendResponse sendVerification(Long userId) {
        // 이 호출이 정상 종료되면 발급 정보는 이미 커밋된 상태다.
        EmailVerificationPrepared prepared =
                transactionService.prepareVerification(userId);

        String content = """
                안녕하세요. MyHome입니다.

                이메일 인증번호를 안내드립니다.

                인증번호: %s

                회원 정보 화면에서 위 번호를 입력해 주세요.
                인증번호는 발급 시점부터 5분 동안 사용할 수 있습니다.
                재발송했다면 가장 최근에 받은 번호를 입력해 주세요.

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(prepared.code());

        try {
            mailService.send(
                    prepared.targetEmail(),
                    "[MyHome] 이메일 인증번호",
                    content
            );
        } catch (MailException | IllegalArgumentException exception) {
            // 예외 본문에는 메일 정보가 들어갈 수 있어 종류만 기록한다.
            log.warn(
                    "이메일 인증 메일 발송 실패: userId={}, exceptionType={}",
                    userId,
                    exception.getClass().getSimpleName()
            );

            try {
                transactionService.revokeAfterSendFailure(
                        userId,
                        prepared.requestId()
                );
            } catch (RuntimeException cleanupException) {
                // 정리 실패도 기록하되 인증번호·메일 본문은 출력하지 않는다.
                log.error(
                        "발송 실패 인증 토큰 무효화 실패: userId={}, exceptionType={}",
                        userId,
                        cleanupException.getClass().getSimpleName()
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "인증 메일을 발송하지 못했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return new EmailVerificationSendResponse(
                prepared.requestId(),
                prepared.expiresAt(),
                prepared.resendAvailableAt()
        );
    }

    /**
     * 트랜잭션 서비스가 검증 결과와 실패 횟수를 먼저 저장한다.
     * 호출이 끝난 후 컨트롤러가 결과를 HTTP 응답으로 변환한다.
     */
    public EmailVerificationConfirmResult confirmVerification(
            Long userId,
            EmailVerificationConfirmRequest request
    ) {
        return transactionService.confirmVerification(userId, request);
    }
}