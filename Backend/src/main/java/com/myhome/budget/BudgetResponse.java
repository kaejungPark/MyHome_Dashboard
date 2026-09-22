package com.myhome.budget;

import java.math.BigDecimal;

/** 월 예산과 실제 지출 현황을 전달한다. */
public record BudgetResponse(
        String month,                // 조회 월
        boolean configured,          // 예산 설정 여부
        BigDecimal budgetAmount,     // 예산 금액: 미설정이면 null
        BigDecimal spentAmount,      // 해당 월 지출 합계
        BigDecimal remainingAmount   // 잔여 예산: 미설정이면 null, 초과 시 음수
) {
}