package com.myhome.ingredient;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 식재료 목록 표시와 수정 폼에 사용하는 조회 결과다.
 * 구매일·소비기한·메모는 미입력 시 null일 수 있다.
 * id는 수정·삭제할 식재료를 식별하는 데 사용한다.
 */
public record IngredientResponse (
        Long id,
        String name,
        BigDecimal quantity,
        String unit,
        String storageLocation,
        LocalDate purchaseDate,
        LocalDate expirationDate,
        String status,
        String memo
) {
}
