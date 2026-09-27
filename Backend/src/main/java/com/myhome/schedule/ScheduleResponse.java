package com.myhome.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일정 목록과 수정 폼에 필요한 정보를 전달한다.
 * 사용자 ID는 서버에서 조회 조건으로 사용하므로 응답에서 생략한다.
 */
public record ScheduleResponse(
        Long id,              // 일정 ID
        String title,         // 일정 제목
        String scheduleType,  // 일정 유형: GENERAL, PAYMENT, RENEWAL
        LocalDate startDate,  // 시작일
        LocalDate dueDate,    // 마감일
        boolean completed,    // 완료 여부
        BigDecimal amount,    // 관련 금액: 없으면 null
        String memo           // 메모: 없으면 null
) {
}