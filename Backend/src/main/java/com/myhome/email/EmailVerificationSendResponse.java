package com.myhome.email;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증번호 발송 성공 후 프론트에 전달하는 정보다.
 * 요청 ID는 인증번호 확인 요청에 사용한다.
 * 시각은 UTC 기준이며 프론트에서 만료·재발송 대기 시간을 계산한다.
 * 원본 인증번호와 해시는 포함하지 않는다.
 */
public record EmailVerificationSendResponse(
        UUID requestId,             // 인증 요청 식별자
        Instant expiresAt,          // 인증번호 만료 시각
        Instant resendAvailableAt   // 재발송 가능 시각
) {
}