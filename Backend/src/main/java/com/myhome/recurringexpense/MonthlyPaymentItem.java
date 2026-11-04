package com.myhome.recurringexpense;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 선택한 월의 납부 예정 정보와 실제 납부 상태를 함께 표시하는 항목이다.
 * paymentDate는 예정일, paidDate는 실제 납부일이다.
 * id는 고정 지출 설정 ID이며 납부 기록 ID가 아니다.
 */
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