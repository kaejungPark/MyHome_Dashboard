package com.myhome.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BudgetUpdateRequest(
        // 월 예산 금액
        @NotNull(message = "예산 금액은 필수입니다.")
        @DecimalMin(value = "0.00", message = "예산 금액은 0 이상이어야 합니다.")
        @Digits(integer = 16, fraction = 2,
                message = "금액은 정수 16자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal amount
) {
}