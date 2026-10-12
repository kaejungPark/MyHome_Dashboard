package com.myhome.ingredient;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 식재료 등록·수정에서 공통으로 사용하는 요청이다.
 * 수량은 양수이며 정수 8자리, 소수 2자리까지 허용한다.
 * 구매일·소비기한·메모는 선택 입력이다.
 * 상태는 보관 중·소비 완료·폐기를 구분하며, 소비기한 임박 여부와는 별개다.
 */
public record IngredientSaveRequest (

        @NotBlank(message = "식재료명을 입력해주세요.")
        @Size(max = 100, message = "식재료명은 100자까지 입력할 수 있습니다.")
                String name,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        BigDecimal quantity,

        @NotBlank(message = "단위를 입력해주세요.")
        @Size(max = 20, message = "단위는 20자까지 입력할 수 있습니다.")
        String unit,

        @NotBlank(message = "보관 장소를 선택해주세요.")
        @Pattern(
                regexp = "REFRIGERATOR|FREEZER|PANTRY",
                message = "올바른 보관 장소를 선택해 주세요."
        )
        String storageLocation,
        LocalDate purchaseDate,
        LocalDate expirationDate,
        @NotBlank(message = "상태를 선택해 주세요.")
        @Pattern(
                regexp = "STORED|CONSUMED|DISPOSED",
                message = "올바른 상태를 선택해 주세요."
        )
        String status,

        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo
) {
}
