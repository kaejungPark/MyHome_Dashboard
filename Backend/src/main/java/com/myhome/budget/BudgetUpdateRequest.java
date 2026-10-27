package com.myhome.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 이미 등록된 월 예산의 금액을 변경하는 요청이다.
 * 대상 월은 URL에서 받으며 금액 검증 기준은 등록과 같다.
 */
public record BudgetUpdateRequest(
        // 월 예산 금액
        @NotNull(message = "예산 금액은 필수입니다.")
        @DecimalMin(value = "0.00", message = "예산 금액은 0 이상이어야 합니다.")
        @Digits(integer = 16, fraction = 2,
                message = "금액은 정수 16자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal amount // 수정할 예산 금액. 필수이며 0 이상, 정수 16자리·소수 2자리까지 허용한다.
) {
}