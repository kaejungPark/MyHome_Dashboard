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

        // 사용자가 관리할 식재료의 이름이다.
        @NotBlank(message = "식재료명을 입력해주세요.")
        @Size(max = 100, message = "식재료명은 100자까지 입력할 수 있습니다.")
        String name,

        // 0보다 큰 수량을 받으며, 0.5kg처럼 소수 수량도 허용한다.
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        BigDecimal quantity,

        // 수량과 함께 표시할 단위다. 예: 개, 팩, kg, L
        @NotBlank(message = "단위를 입력해주세요.")
        @Size(max = 20, message = "단위는 20자까지 입력할 수 있습니다.")
        String unit,

        // 보관 장소 코드다. 냉장·냉동·실온 중 하나를 선택한다.
        @NotBlank(message = "보관 장소를 선택해주세요.")
        @Pattern(
                regexp = "REFRIGERATOR|FREEZER|PANTRY",
                message = "올바른 보관 장소를 선택해 주세요."
        )
        String storageLocation,

        // 구매일: 선택 입력이며 입력하지 않으면 null이다.
        LocalDate purchaseDate,

        // 소비기한: 선택 입력이며 화면에서 남은 일수 안내에 사용한다.
        LocalDate expirationDate,

        // 사용자가 지정하는 관리 상태다. 소비기한이 지나도 자동으로 폐기로 변경되지는 않는다.
        @NotBlank(message = "상태를 선택해 주세요.")
        @Pattern(
                regexp = "STORED|CONSUMED|DISPOSED",
                message = "올바른 상태를 선택해 주세요."
        )
        String status,

        // 보관 방법 등 추가 설명을 저장하는 선택 항목이다.
        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo
) {
}
