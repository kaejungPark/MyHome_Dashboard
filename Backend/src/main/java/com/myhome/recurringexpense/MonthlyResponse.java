package com.myhome.recurringexpense;

import java.math.BigDecimal;
import java.util.List;

/**
 * 선택한 월의 고정 지출 목록과 예정 금액 합계다.
 * 목록에는 납부 완료 항목도 포함되며, totalAmount는 미납 잔액이 아니다.
 */
public record  MonthlyResponse(
        String month,                      // 조회 월: YYYY-MM
        BigDecimal totalAmount,            // 납부 예정 금액 합계
        List<MonthlyPaymentItem> items     // 납부 완료 여부를 포함한 월별 고정 지출 목록
) {

}
