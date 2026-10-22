package com.myhome.email;

import java.time.Instant;
import java.util.UUID;

/**
 * DB 저장을 마친 인증번호 발급 정보다.
 * 원본 인증번호를 포함하므로 API 응답으로 반환하지 않는다.
 */
public record EmailVerificationPrepared(
        Long userId,                // 인증번호를 요청한 회원 ID
        UUID requestId,             // 이번 발급 건의 식별자
        String targetEmail,         // 인증번호를 발송할 회원 이메일
        String code,                // 이메일 본문에만 사용할 원본 인증번호
        Instant expiresAt,          // 인증번호 만료 시각(UTC)
        Instant resendAvailableAt   // 다음 발송 요청이 가능한 시각(UTC)
) {
    /**
     * 실수로 객체를 로그에 출력해도 인증번호와 이메일을 노출하지 않는다.
     */
    @Override
    public String toString() {
        return "EmailVerificationPrepared[redacted]";
    }
}