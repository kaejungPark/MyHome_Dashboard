package com.myhome.recurringexpense;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 선택한 월에 납부할 고정 지출 한 건의 정보다. */
public record MonthlyPaymentItem(
        Long id,                 // 고정 지출 ID
        Long categoryId,         // 카테고리 ID
        String categoryName,     // 카테고리명
        String title,            // 항목명
        BigDecimal amount,       // 납부 예정 금액
        LocalDate paymentDate,   // 납부 예정일
        String paymentMethod,// 결제 수단
        boolean paid,            // 납부 기록이 있으면 true
        Long expenseId,           // 연결된 생활비 ID, 미납이면 null
        LocalDate paidDate        // 실제 납부일, 미납이면 null
) {
}