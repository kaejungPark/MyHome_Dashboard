package com.myhome.householdtask;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HouseholdTaskCompleteRequest (

        // 실제 완료일은 오늘 또는 과거 날짜만 허용한다.
        @NotNull(message = "완료일을 입력해 주세요.")
        @PastOrPresent(message = "완료일은 오늘 또는 이전 날짜로 입력해 주세요.")
        LocalDate completedDate,

        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo
) {
}
