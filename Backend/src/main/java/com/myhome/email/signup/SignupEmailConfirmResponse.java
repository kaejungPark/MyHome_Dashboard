package com.myhome.email.signup;

import java.time.Instant;

/**
 * 이메일 인증 성공 후 회원가입에 사용할 일회용 확인값을 반환한다.
 */
public record SignupEmailConfirmResponse(
        String verificationToken, // 회원가입 요청에 전달할 일회용 확인값
        Instant expiresAt         // 가입 확인값 만료 시각
) {
    // 가입 확인값이 로그에 출력되지 않도록 한다.
    @Override
    public String toString() {
        return "SignupEmailConfirmResponse[redacted]";
    }
}