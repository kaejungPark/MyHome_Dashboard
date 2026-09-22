package com.myhome.expense;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 지출 수정 요청. */
public record ExpenseUpdateRequest(
        @NotNull @Positive
        Long categoryId,          // 카테고리 ID

        @NotNull @DecimalMin("0.01")
        @Digits(integer = 16, fraction = 2)
        BigDecimal amount,       // 지출 금액

        @NotNull
        LocalDate expenseDate,   // 지출일

        @NotBlank @Pattern(regexp = "FIXED|VARIABLE")
        String expenseType,      // 고정 또는 변동 지출

        @Size(max = 50)
        String paymentMethod,    // 결제 수단

        @NotBlank @Size(max = 200)
        String title,            // 지출 제목

        @Size(max = 2000)
        String memo              // 메모
) {
}