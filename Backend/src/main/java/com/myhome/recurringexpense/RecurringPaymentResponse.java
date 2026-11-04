package com.myhome.recurringexpense;

import java.time.LocalDate;

/**
 * 월별 납부 기록과 연결된 생활비를 확인하는 조회 결과다.
 * 실제 납부일은 연결된 생활비의 지출일에서 가져온다.
 */
public record RecurringPaymentResponse(
        Long recurringExpenseId, // 납부한 고정 지출 설정 ID
        Long expenseId,          // 납부 완료 시 생성된 생활비 ID
        LocalDate paidDate      // 실제 납부일
) {
}