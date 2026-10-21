package com.myhome.admin;

import java.time.LocalDate;

/**
 * 관리자 대시보드에 표시할 날짜별 가입자 수다.
 */
public record AdminSignupTrendResponse(
        LocalDate date,      // 한국 시간 기준 가입 날짜
        long signupCount     // 해당 날짜의 가입자 수
) {
}