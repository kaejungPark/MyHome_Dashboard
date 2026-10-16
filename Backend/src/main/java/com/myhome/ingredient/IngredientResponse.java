package com.myhome.ingredient;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 식재료 목록 표시와 수정 폼에 사용하는 조회 결과다.
 * 구매일·소비기한·메모는 미입력 시 null일 수 있다.
 * id는 수정·삭제할 식재료를 식별하는 데 사용한다.
 */
public record IngredientResponse (
        Long id,                    // 식재료 ID
        String name,                // 식재료명
        BigDecimal quantity,        // 수량. 소수점 수량을 표현하기 위해 BigDecimal을 사용한다.
        String unit,                // 수량의 단위. 예: 개, kg, g, L
        String storageLocation,     // 보관 장소. REFRIGERATOR: 냉장, FREEZER: 냉동, PANTRY: 실온
        LocalDate purchaseDate,     // 구매일. 미입력 시 null
        LocalDate expirationDate,   // 소비기한. 미입력 시 null
        String status,              // 관리 상태. STORED: 보관 중, CONSUMED: 소비 완료, DISPOSED: 폐기
        String memo                 // 메모
) {
}
