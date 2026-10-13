package com.myhome.householdtask;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HouseholdTaskHistoryResponse (
        Long id,                    // 완료 이력 ID
        Long taskId,                // 관리 항목 ID
        LocalDate completedDate,    // 실제 완료일
        String memo,                // 완료 메모
        LocalDateTime createdDt     // 기록 생성 일시
) {
}
