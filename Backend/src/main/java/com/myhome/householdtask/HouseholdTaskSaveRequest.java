package com.myhome.householdtask;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record HouseholdTaskSaveRequest (

        // 청소·교체·보충 등 관리할 항목의 이름이다.
        @NotBlank(message = "항목명을 입력해 주세요.")
        @Size(max = 100, message = "항목명은 100자까지 입력할 수 있습니다.")
        String name,

        // CHORE는 집안일, CONSUMABLE은 소모품 교체·보충을 의미한다.
        @NotBlank(message = "관리 유형을 선택해 주세요.")
        @Pattern(
                regexp = "CHORE|CONSUMABLE",
                message = "관리 유형은 집안일 또는 소모품이어야 합니다."
        )
        String taskType,

        // 반복 주기를 일 단위로 입력받는다.
        @NotNull(message = "반복 주기를 입력해 주세요.")
        @Min(value = 1, message = "반복 주기는 1일 이상이어야 합니다.")
        Integer cycleDays,

        // 지난 예정일도 등록할 수 있도록 날짜 범위는 제한하지 않는다.
        @NotNull(message = "다음 예정일을 입력해 주세요.")
        LocalDate nextDueDate,

        // true는 사용, false는 비활성 상태다.
        @NotNull(message = "사용 여부를 선택해 주세요.")
        Boolean active,

        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo

) {
}
