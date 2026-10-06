package com.myhome.dashborad;

import com.myhome.common.validation.MonthValidator;
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
import java.time.format.DateTimeFormatter;
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

        List<UpcomingPaymentResponse> payment =   findPayment(userId);

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
                items,                         // 보증 종료 예정 물품 목록
                payment
        );
    }

    /**
     * 오늘부터 7일 뒤까지 납부 예정인 미납 고정 지출을 조회한다.
     * 조회 기간이 다음 달에 걸치면 다음 달 목록도 조회하여 합친다.
     * 두 조회 모두 같은 오늘 날짜를 기준으로 기간을 제한한다.
     */
    @Transactional(readOnly = true)
    public List<UpcomingPaymentResponse> findPayment(Long userId) {
        // 조회를 위한 당일날짜 정의
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 조회를 위한 당일날짜 + 7일 정의 => 만약 7일후가 다음달로 넘어갈 수 있기 때문
        LocalDate sevenDaysLater = today.plusDays(7);

        // yyyy-MM 포맷터 생성
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // 오늘 날짜를 포맷에 맞게 문자열로 변환
        String currentMonth = today.format(formatter);

        // 조회 월을 검증하고 해당 월 1일로 변환한다.
        LocalDate paymentMonth = MonthValidator.parseMonth(currentMonth).atDay(1);

        // 조회 종료일인 7일 뒤가 속한 월의 1일을 구한다.
        LocalDate paymentNextMonth = MonthValidator.parseMonth(currentMonth).atDay(1);

        // 1. 첫 번째 달 조회
        List<UpcomingPaymentResponse> paymentList = dashboardMapper.findPaymentByUserId(userId, today, paymentMonth);

        // 2. 달이 다르면 두 번째 달도 조회해서 기존 리스트에 추가
        if (!paymentMonth.equals(paymentNextMonth)) {
            List<UpcomingPaymentResponse> nextPaymentList = dashboardMapper.findPaymentByUserId(userId, today, paymentNextMonth);
            paymentList.addAll(nextPaymentList); // 리스트 합치기
        }

        return paymentList;

    }
}
