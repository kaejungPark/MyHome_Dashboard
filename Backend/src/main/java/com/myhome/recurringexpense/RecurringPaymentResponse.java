package com.myhome.recurringexpense;

import java.time.LocalDate;

/** 월별 납부 기록과 연결된 생활비 정보를 조회한다. */
public record RecurringPaymentResponse(
        Long recurringExpenseId,
        Long expenseId,
        LocalDate paidDate
) {
}