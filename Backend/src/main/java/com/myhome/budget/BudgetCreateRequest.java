package com.myhome.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 월 예산 등록에 사용할 금액이다.
 * 대상 월은 요청 본문이 아닌 URL에서 받는다.
 */
public record BudgetCreateRequest(
        // 금액은 필수이며 0원도 등록할 수 있다.
        // 정수 16자리, 소수 2자리까지 허용한다.
        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 16, fraction = 2)
        BigDecimal amount
) {
}