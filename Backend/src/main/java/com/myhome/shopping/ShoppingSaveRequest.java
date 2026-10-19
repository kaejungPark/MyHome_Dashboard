package com.myhome.shopping;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 장보기 등록·수정에 사용하는 입력값이다.
 * 구매 여부와 실제 구매일은 별도 완료·취소 기능에서 관리하므로 포함하지 않는다.
 */
public record ShoppingSaveRequest (
        @NotBlank(message = "구매할 물품명을 입력해 주세요.")
        @Size(max = 100, message = "구매할 물품명은 100자까지 입력할 수 있습니다.")
        String name,

        // 수량은 0보다 커야 하며, 정수 8자리·소수 2자리까지 허용한다.
        @NotNull(message = "수량을 입력해 주세요.")
        @DecimalMin(value = "0", inclusive = false, message = "수량은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "수량은 정수 8자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal quantity,

        @NotBlank(message = "단위를 입력해 주세요.")
        @Size(max = 20, message = "단위는 20자까지 입력할 수 있습니다.")
        String unit,

        // 구매 예정일은 선택 입력이며, 미입력 시 날짜 기준 알림에서 제외한다.
        LocalDate purchaseDueDate,

        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo
){
}
