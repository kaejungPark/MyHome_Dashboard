package com.myhome.email.signup;

import java.time.LocalDateTime;

/**
 * 인증번호 검증에 필요한 가입 전 인증 이력을 조회한다.
 * DB의 DATETIME2 값은 UTC 기준으로 저장하고 해석한다.
 */
public record SignupEmailToken(
        Long id,
        String email,
        String codeHash,
        int failedAttempts,
        LocalDateTime expiresDt,
        LocalDateTime verifiedDt,
        LocalDateTime usedDt,
        LocalDateTime revokedDt
) {
    @Override
    public String toString() {
        return "SignupEmailToken[id=" + id + "]";
    }
}