package com.myhome.recurringexpense;

import java.math.BigDecimal;
import java.util.List;

public record  MonthlyResponse(
        String month,                                      // 조회 월: YYYY-MM
        BigDecimal totalAmount,                            // 납부 예정 금액 합계
        List<MonthlyPaymentItem> items     // 납부 예정 목록
) {

}
