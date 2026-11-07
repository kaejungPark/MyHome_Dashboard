package com.myhome.shopping;

import java.time.LocalDateTime;

/**
 * 사용자 한 명의 알림 실행일에 해당하는 발송 이력 한 건을 담는다.
 * 서비스는 이력 유무로 당일 발송 여부를 판단하고,
 * 발송 후에는 이력 ID와 시도 횟수로 이번 처리의 성공·실패를 기록한다.
 * updatedDt는 DB에 저장한 마지막 상태 변경 시각이며 UTC 기준이다.
 */
public record ShoppingNotificationHistory(
        Long id,                  // 발송 이력 ID
        String status,            // PROCESSING, SENT, FAILED
        Integer attemptCount,     // 현재까지의 발송 시도 횟수
        LocalDateTime updatedDt   // 마지막 상태 변경 시각
) {
}