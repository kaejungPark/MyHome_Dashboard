package com.myhome.recurringexpense;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * 고정 지출 납부 완료 요청이다.
 * 납부 대상 ID와 월은 URL로, 사용자 ID는 로그인 정보에서 가져온다.
 */
public record RecurringPaymentRequest(

        // 실제 납부일이며, 자동 생성할 생활비의 지출일로 사용한다.
        @NotNull(message = "납부일은 필수입니다.")
        @PastOrPresent(message = "납부일은 오늘 또는 이전 날짜로 입력해 주세요.")
        LocalDate paymentDate

) {
}