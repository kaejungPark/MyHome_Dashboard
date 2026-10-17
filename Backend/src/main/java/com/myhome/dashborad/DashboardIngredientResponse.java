package com.myhome.dashborad;

import java.time.LocalDate;

/**
 * 대시보드에서 소비기한 확인이 필요한 식재료 한 건을 표시하는 응답이다.
 * 보관 중이며 소비기한이 등록된 항목만 조회한다.
 * 기한이 지난 항목을 포함해 오늘 기준 3일 뒤까지의 식재료를 대상으로 한다.
 */
public record DashboardIngredientResponse (
        Long id,                    // 식재료 ID
        String name,                // 식재료명
        String storageLocation,     // 보관 장소. REFRIGERATOR: 냉장, FREEZER: 냉동, PANTRY: 실온
        LocalDate expirationDate    // 소비기한. 대시보드 조회는 미등록 항목을 제외한다.
){

}
