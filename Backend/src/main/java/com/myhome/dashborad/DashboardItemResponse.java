package com.myhome.dashborad;

import java.time.LocalDate;

/**
 * 물품 목록과 수정 화면에 필요한 정보를 전달한다.
 * 구매 정보, 보증 종료일, 관리 주기 등 선택 항목은 null일 수 있다.
 */
public record DashboardItemResponse(
    Long id,                    // 물품 ID
    String name,                // 물품명
    LocalDate warrantyEndDate   // 보증 종료일
) {
}
