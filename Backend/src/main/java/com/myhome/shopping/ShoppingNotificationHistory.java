package com.myhome.shopping;

import java.time.LocalDateTime;

/**
 * 발송 가능 여부와 재시도 판단에 필요한 알림 이력 정보를 담는다.
 * updatedDt는 DB에 저장된 UTC 시각이다.
 */
public record ShoppingNotificationHistory(
        Long id,                  // 발송 이력 ID
        String status,            // PROCESSING, SENT, FAILED
        Integer attemptCount,     // 현재까지의 발송 시도 횟수
        LocalDateTime updatedDt   // 마지막 상태 변경 시각
) {
}