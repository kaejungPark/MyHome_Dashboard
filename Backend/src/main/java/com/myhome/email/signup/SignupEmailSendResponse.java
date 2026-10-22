package com.myhome.email.signup;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증번호 발송 후 화면에서 사용할 정보를 반환한다.
 */
public record SignupEmailSendResponse(
        UUID requestId,           // 인증번호 확인 요청에 사용할 식별자
        Instant expiresAt,        // 인증번호 만료 시각
        Instant resendAvailableAt // 재발송할 수 있는 시각
) {
}