package com.myhome.dashborad;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 납부일이 가까운 미납 고정 지출 한 건이다. */
public record UpcomingPaymentResponse(
        Long id,               // 고정 지출 ID
        String title,          // 항목명
        BigDecimal amount,     // 납부 예정 금액
        LocalDate paymentDate  // 말일 보정된 납부 예정일
) {
}