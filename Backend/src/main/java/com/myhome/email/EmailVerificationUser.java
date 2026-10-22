package com.myhome.email;

import java.time.LocalDateTime;

/**
 * 이메일 인증에 필요한 회원 정보다.
 * 이메일 인증 완료 시각은 UTC 기준이며 미인증이면 null이다.
 */
public record EmailVerificationUser(
        Long userId,                  // 로그인한 회원 ID
        String email,                 // DB에 저장된 현재 이메일
        String status,                // 회원 상태. ACTIVE인 경우에만 인증을 진행한다.
        LocalDateTime emailVerifiedDt // 이메일 인증 완료 시각(UTC). 미인증이면 null.
) {
}