package com.myhome.householdtask;

import java.time.LocalDate;

/**
 * 집안일·소모품 관리 목록과 수정 폼에 사용하는 조회 응답이다.
 * 반복 주기는 일 단위이며, 완료 처리 시 실제 완료일에 주기를 더해 다음 예정일을 갱신한다.
 * active는 사용 여부를 나타내며, 완료 여부나 완료 이력 유무를 의미하지 않는다.
 */
public record HouseholdTaskResponse(
        Long id,              // 집안일·소모품 ID. 수정·삭제·완료 처리·이력 조회에 사용한다.
        String name,          // 항목명
        String taskType,      // 유형. CHORE: 집안일, CONSUMABLE: 소모품
        Integer cycleDays,    // 반복 주기. 1일 이상의 정수
        LocalDate nextDueDate,// 다음 작업 또는 교체 예정일
        Boolean active,       // 사용 여부. true: 사용 중, false: 비활성
        String memo           // 추가 메모. 미입력 시 null
) {
}