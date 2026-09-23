package com.myhome.recurringexpense;

import java.math.BigDecimal;

public record RecurringExpenseResponse(
        Long id,                 // 고정 지출 ID
        Long categoryId,         // 카테고리 ID
        String categoryName,     // 카테고리명
        String title,            // 항목명
        BigDecimal amount,       // 매월 납부 예정 금액
        Integer paymentDay,      // 매월 납부일
        String paymentMethod,    // 결제 수단
        String startMonth,       // 시작 월: YYYY-MM
        String endMonth,         // 종료 월: 없으면 null
        boolean active,          // 사용 여부
        String memo              // 메모
) {
}