package com.myhome.expense;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 생활비 수정 화면에서 전달하는 입력값이다.
 * 등록과 동일한 입력 기준을 적용한다.
 * 수정할 생활비 ID는 URL에서 받고, 사용자 ID는 로그인 정보에서 가져온다.
 */
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