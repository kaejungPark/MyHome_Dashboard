package com.myhome.dashborad;

import java.time.LocalDate;

/**
 * Dashboard에서의 일정 조회
 * 사용자 ID는 서버에서 조회 조건으로 사용하므로 응답에서 생략한다.
 */
public record DashboardScheduleResponse (
        Long id,                // 일정 ID
        String title,           // 일정 제목
        String scheduleType,    // 일정 유형: GENERAL, PAYMENT, RENEWAL
        LocalDate dueDate       // 마감일
){
}
