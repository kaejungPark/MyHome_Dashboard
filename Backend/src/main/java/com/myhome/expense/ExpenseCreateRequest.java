package com.myhome.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 지출 등록 요청.
 * 필수값과 길이, 금액, 지출 유형을 검증한다.
 */
public record ExpenseCreateRequest(
        @NotNull
        @Positive
        Long categoryId,          // 카테고리 ID

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 16, fraction = 2)
        BigDecimal amount,       // 지출 금액: 0보다 큰 값

        @NotNull
        LocalDate expenseDate,   // 지출일

        @NotBlank
        @Pattern(regexp = "FIXED|VARIABLE")
        String expenseType,      // FIXED: 고정 지출, VARIABLE: 변동 지출

        @Size(max = 50)
        String paymentMethod,    // 결제 수단: 선택 입력

        @NotBlank
        @Size(max = 200)
        String title,            // 지출 제목

        @Size(max = 2000)
        String memo              // 메모: 선택 입력
) {
}