package com.myhome.dashborad;

import java.time.LocalDate;

/**
 * 대시보드에서 처리할 집안일·소모품 한 건을 표시하는 응답이다.
 * 활성 항목 중 예정일이 지났거나 오늘부터 7일 이내인 항목을 대상으로 한다.
 * 다음 예정일이 없는 항목은 대시보드 조회에서 제외한다.
 */
public record DashboardHouseholdTaskResponse(
        Long id,              // 집안일·소모품 ID
        String name,          // 항목명
        String taskType,      // 유형. CHORE: 집안일, CONSUMABLE: 소모품
        LocalDate nextDueDate // 다음 예정일
) {
}