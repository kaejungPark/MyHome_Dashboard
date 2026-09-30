package com.myhome.homeItem;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dashboard에서의 물품 조회
 * 구매 정보, 보증 종료일, 관리 주기 등 선택 항목은 null일 수 있다.
 */
public record HomeItemResponse(
        Long id,                   // 물품 ID
        String name,               // 물품명
        String category,           // 물품 분류: 가전, 가구 등
        LocalDate purchaseDate,    // 구매일
        BigDecimal purchasePrice,  // 구매 금액
        LocalDate warrantyEndDate, // 보증 종료일
        Integer maintenanceCycle,  // 관리 주기: 일 단위
        String status,             // 상태: IN_USE(사용 중), STORED(보관 중), DISPOSED(처분)
        String memo                // 메모
) {
}