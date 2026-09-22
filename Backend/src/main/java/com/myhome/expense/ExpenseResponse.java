package com.myhome.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 지출 목록 조회 응답.
 * 금액은 소수점 계산 오차를 피하기 위해 BigDecimal을 사용한다.
 */
public record ExpenseResponse(
        Long id,                 // 지출 ID
        Long categoryId,         // 카테고리 ID
        String categoryName,     // 카테고리명
        BigDecimal amount,       // 지출 금액
        LocalDate expenseDate,   // 지출일
        String expenseType,      // 지출 유형: FIXED(고정 지출), VARIABLE(변동 지출)
        String paymentMethod,    // 결제 수단
        String title,            // 지출 제목
        String memo              // 메모
) {
}