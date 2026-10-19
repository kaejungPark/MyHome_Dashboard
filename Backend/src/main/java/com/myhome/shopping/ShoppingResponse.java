package com.myhome.shopping;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 장보기 목록에 표시할 항목 정보와 구매 상태를 반환한다.
 */
public record ShoppingResponse (
    Long id,                    // 장보기 항목 ID, PK·자동 증가
    String name,                // 구매할 물품명
    BigDecimal quantity,        // 구매할 수량, 0보다 큰 값
    String unit,                // 단위: 개·팩·kg 등
    Boolean isCompleted,        // 구매 완료 여부: true = 구매 완료, false = 미구매
    LocalDate completedDate,    // 실제 구매일, 미구매이면 null
    LocalDate purchaseDueDate,  // 구매해야 할 예정 날짜
    String memo                 // 추가 메모
){
}
