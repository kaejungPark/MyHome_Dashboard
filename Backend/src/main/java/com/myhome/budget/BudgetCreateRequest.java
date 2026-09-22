package com.myhome.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 예산 등록 요청. 월은 URL에서 별도로 받는다. */
public record BudgetCreateRequest(
        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 16, fraction = 2)
        BigDecimal amount // 월 예산 금액
) {
}