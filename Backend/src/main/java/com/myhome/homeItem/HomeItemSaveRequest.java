package com.myhome.homeItem;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 물품 등록과 수정에 함께 사용하는 입력값이다.
 * 물품명과 상태는 필수이며 나머지 항목은 입력하지 않으면 null을 받을 수 있다.
 * 선택 항목도 값이 입력되면 길이·금액·관리 주기 조건을 검사한다.
 */
public record HomeItemSaveRequest (
        // 물품명
        @NotBlank(message = "물품명은 필수입니다.")
        @Size(max = 200, message = "물품명은 200자까지 입력할 수 있습니다.")
        String name,

        // 물품 분류
        @Size(max = 100, message = "물품 분류는 100자까지 입력할 수 있습니다.")
        String category,

        // 구매일: 선택 입력
        LocalDate purchaseDate,

        // 구매 금액: 선택 입력이며, 입력한 경우 0 이상·정수 16자리·소수 2자리까지 허용한다.
        @DecimalMin(value = "0.00", message = "구매 금액은 0 이상이어야 합니다.")
        @Digits(integer = 16, fraction = 2, message = "구매 금액은 정수 16자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal purchasePrice,

        // 보증 종료일: 선택 입력
        LocalDate warrantyEndDate,

        // 관리 주기: 선택 입력이며, 입력한 경우 1일 이상의 정수여야 한다.
        @Min(1)
        Integer maintenanceCycle,

        // 상태: IN_USE(사용 중), STORED(보관 중), DISPOSED(처분)
        @NotBlank(message = "상태 선택은 필수입니다.")
        @Pattern(regexp = "IN_USE|STORED|DISPOSED")
        String status,

        // 메모: 선택 입력
        @Size(max = 2000, message = "메모는 2000자까지 입력할 수 있습니다.")
        String memo

){
}
