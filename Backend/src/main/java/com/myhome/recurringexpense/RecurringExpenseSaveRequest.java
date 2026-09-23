package com.myhome.recurringexpense;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RecurringExpenseSaveRequest(
        // 지출 카테고리 ID
        @NotNull(message = "카테고리는 필수입니다.")
        @Positive(message = "카테고리 ID는 양수여야 합니다.")
        Long categoryId,

        // 고정 지출 항목명
        @NotBlank(message = "항목명은 필수입니다.")
        @Size(max = 200, message = "항목명은 200자까지 입력할 수 있습니다.")
        String title,

        // 매월 납부 예정 금액
        @NotNull(message = "금액은 필수입니다.")
        @DecimalMin(value = "0.01", message = "금액은 0보다 커야 합니다.")
        @Digits(integer = 16, fraction = 2,
                message = "금액은 정수 16자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal amount,

        // 매월 납부일
        @NotNull(message = "납부일은 필수입니다.")
        @Min(value = 1, message = "납부일은 1 이상이어야 합니다.")
        @Max(value = 31, message = "납부일은 31 이하여야 합니다.")
        Integer paymentDay,

        // 결제 수단: 카드, 계좌이체 등
        @Size(max = 50, message = "결제 수단은 50자까지 입력할 수 있습니다.")
        String paymentMethod,

        // 시작 월: YYYY-MM 형식
        @NotBlank(message = "시작 월은 필수입니다.")
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])",
                message = "시작 월은 YYYY-MM 형식이어야 합니다.")
        String startMonth,

        // 종료 월: 종료 기한이 없으면 null
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])",
                message = "종료 월은 YYYY-MM 형식이어야 합니다.")
        String endMonth,

        // 사용 여부: true이면 예정 금액 계산에 포함
        @NotNull(message = "사용 여부는 필수입니다.")
        Boolean active,

        // 메모
        @Size(max = 2000, message = "메모는 2000자까지 입력할 수 있습니다.")
        String memo
) {
}