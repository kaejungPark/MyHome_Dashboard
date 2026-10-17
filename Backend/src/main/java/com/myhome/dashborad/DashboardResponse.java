package com.myhome.dashborad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse (
        LocalDate today,                                    // 조회 날짜 (당일)
        String month,                                       // 조회 월 YYYY-MM
        BigDecimal totalAmount,                             // 이번 달 실제 지출 합계
        boolean configured,                                 // 이번 달 예산 등록 여부
        BigDecimal budgetAmount,                            // 월 예산, 미등록이면 null
        BigDecimal remainingAmount,                         // 잔여 예산, 미등록이면 null
        BigDecimal usageRate,                               // 예산 사용률, 예산 미등록·0원이면 null
        BigDecimal recurringAmount,                         // 이번 달 고정 지출 예정 합계
        List<DashboardScheduleResponse> schedules,          // 마감이 가까운 미완료 일정
        int scheduleCount,                                  // 같은 조회 조건에 해당하는 전체 항목 건수
        List<DashboardItemResponse> items,                  // 보증 종료가 가까운 물품
        List<UpcomingPaymentResponse> payments,             // 7일 이내 납부 예정인 미납 고정 지출, 최대 3건
        int paymentCount,                                   // 같은 조회 조건에 해당하는 전체 미납 건수
        List<DashboardIngredientResponse> ingredients,      // 소비기한 확인 대상 식재료 목록, 최대 3건
        int ingredientCount,                                // 같은 조회 조건에 해당하는 전체 식재료 건수
        List<DashboardHouseholdTaskResponse> householdTasks,// 예정일 경과·7일 이내인 활성 항목, 최대 3건
        int householdTaskCount                              // 같은 조회 조건에 해당하는 전체 항목 건수                  //
) {

}
