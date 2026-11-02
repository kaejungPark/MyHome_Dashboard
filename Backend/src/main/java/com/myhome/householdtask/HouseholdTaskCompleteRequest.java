package com.myhome.householdtask;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 한 번의 완료 처리에 사용할 실제 완료일과 메모를 전달한다.
 * 다음 예정일은 요청으로 받지 않고 서버에서 완료일과 저장된 반복 주기로 계산한다.
 */
public record HouseholdTaskCompleteRequest (

        // 실제 완료일은 오늘 또는 과거 날짜만 허용한다.
        @NotNull(message = "완료일을 입력해 주세요.")
        @PastOrPresent(message = "완료일은 오늘 또는 이전 날짜로 입력해 주세요.")
        LocalDate completedDate,

        // 이번 청소·교체·보충 작업에 대한 메모이며, 관리 항목의 메모는 변경하지 않는다.
        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo
) {
}
