package com.myhome.householdtask;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 완료 이력 목록에 표시할 한 건의 기록이다.
 * id는 완료 기록의 번호이고, taskId는 해당 기록이 연결된 관리 항목의 번호다.
 * completedDate는 실제 완료일이며, createdDt는 기록이 DB에 저장된 일시다.
 */
public record HouseholdTaskHistoryResponse (
        Long id,                    // 완료 이력 ID
        Long taskId,                // 관리 항목 ID
        LocalDate completedDate,    // 실제 완료일
        String memo,                // 완료 메모
        LocalDateTime createdDt     // 기록 생성 일시
) {
}
