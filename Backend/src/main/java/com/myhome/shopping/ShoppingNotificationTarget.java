package com.myhome.shopping;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 장보기 알림 메일에 사용할 수신 회원 정보와 개별 구매 예정 항목을 담는다.
 */
public record ShoppingNotificationTarget (
        Long userId,                // 사용자별로 항목을 묶는 기준
        String email,               // 메일 수신 주소
        Long shoppingItemId,        // 장보기 항목 ID
        String name,                // 구매할 물품명
        BigDecimal quantity,        // 수량
        String unit,                // 단위
        LocalDate purchaseDueDate   // 오늘·내일 구매 예정 구분
) {
}
