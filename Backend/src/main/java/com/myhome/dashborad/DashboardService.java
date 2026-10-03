package com.myhome.dashborad;

import com.myhome.recurringexpense.MonthlyResponse;
import com.myhome.recurringexpense.RecurringExpenseService;
import com.myhome.statistics.StatisticsResponse;
import com.myhome.statistics.StatisticsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final StatisticsService statisticsService;
    private final RecurringExpenseService recurringExpenseService;

    public DashboardService (
            DashboardMapper dashboardMapper,
            StatisticsService statisticsService,
            RecurringExpenseService recurringExpenseService)
    {
        this.dashboardMapper = dashboardMapper;
        this.statisticsService = statisticsService;
        this.recurringExpenseService = recurringExpenseService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse findDashboards(Long userId) {

        // 조회를 위한 당일날짜 정의
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 오늘부터 30일 뒤까지 마감되는 미완료 일정을 조회한다.
        LocalDate endDate = today.plusDays(30);

        // 조회 월을 YYYY-MM 문자열로 변환한다.
        String month = YearMonth.from(today).toString();

        // 기존 통계 서비스를 이용해 이번 달 통계를 조회한다.
        StatisticsResponse statistics = statisticsService.findStatistics(userId, month);

        // 반환된 객체에서 필요한 값을 꺼낸다.
        BigDecimal totalAmount = statistics.totalAmount();

        // 고정 지출 월별 예정 조회
        MonthlyResponse monthly = recurringExpenseService.getMonthlyExpenses(userId, month);

        // 오늘 기준 30일 뒤까지 마감되는 미완료 일정을 최대 5개 조회한다.
        List<DashboardScheduleResponse> schedules = dashboardMapper.findSchedules(userId, today, endDate);

        // 오늘 기준 30일 뒤까지 보증이 종료되는 미처분 물품을 최대 5개 조회한다.
        List<DashboardItemResponse> items = dashboardMapper.findItems(userId, today, endDate);

        // 조회한 요약 정보와 일정·물품 목록을 하나의 응답으로 반환한다.
        return new DashboardResponse(
                today,                        // 조회 기준 날짜
                month,                        // 조회 월
                statistics.totalAmount(),     // 이번 달 실제 지출
                statistics.configured(),      // 예산 등록 여부
                statistics.budgetAmount(),    // 월 예산
                statistics.remainingAmount(), // 잔여 예산
                statistics.usageRate(),       // 예산 사용률
                monthly.totalAmount(),        // 고정 지출 예정 합계
                schedules,                    // 마감이 가까운 일정 목록
                items                         // 보증 종료 예정 물품 목록
        );
    }
}
