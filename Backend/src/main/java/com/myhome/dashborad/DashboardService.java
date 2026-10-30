package com.myhome.dashborad;

import com.myhome.common.validation.MonthValidator;
import com.myhome.recurringexpense.RecurringExpenseService;
import com.myhome.statistics.StatisticsResponse;
import com.myhome.statistics.StatisticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Comparator;

/**
 * 여러 기능의 요약 정보를 모아 대시보드 응답을 만든다.
 * 생활비 통계와 고정 지출 합계는 기존 서비스를 이용하고,
 * 확인이 필요한 일정·물품·식재료·집안일은 매퍼로 조회한다.
 */
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

    /**
     * 로그인한 사용자의 이번 달 지출·예산과 생활 관리 요약을 조회한다.
     * 고정 지출 예정 합계는 실제 지출과 구분하여 제공한다.
     *
     * 일정·식재료·집안일·미납 항목은 대표 목록과 전체 건수를 따로 반환한다.
     * 따라서 화면에 표시된 목록 개수와 전체 건수는 다를 수 있다.
     */
    @Transactional(readOnly = true)
    public DashboardResponse findDashboards(Long userId) {

        // 한국 시간 기준 오늘 날짜를 조회 기준으로 사용한다.
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 일정과 집안일·소모품 조회에 사용할 오늘 기준 7일 뒤 날짜를 계산한다.
        LocalDate sevenEndDate = today.plusDays(7);

        // 기존 보증 종료 예정 물품 조회의 30일 기준을 유지한다.
        LocalDate endDate = today.plusDays(30);

        // 소비기한이 지난 항목을 포함해 오늘 기준 3일 뒤까지 조회한다.
        LocalDate ingredientEndDate = today.plusDays(3);

        // 조회 월을 YYYY-MM 문자열로 변환한다.
        String month = YearMonth.from(today).toString();

        // 기존 통계 서비스를 이용해 이번 달 통계를 조회한다.
        StatisticsResponse statistics = statisticsService.findStatistics(userId, month);

        // 이번 달에 납부하도록 예정된 고정 지출 합계다.
        // 실제 지출 합계에 더하지 않고 별도 정보로 표시한다.
        BigDecimal recurringAmount = recurringExpenseService.getMonthlyTotalAmount(userId, month);

        // 기한이 지났거나 7일 이내 마감되는 미완료 일정을 최대 3건 조회한다.
        List<DashboardScheduleResponse> schedules = dashboardMapper.findSchedules(userId, sevenEndDate);

        // 화면에 표시할 최대 3건과 별도로, 같은 조건의 전체 일정 건수를 조회한다.
        int scheduleCount = dashboardMapper.countSchedules(userId, sevenEndDate);

        // 오늘 기준 30일 뒤까지 보증이 종료되는 미처분 물품을 최대 5개 조회한다.
        List<DashboardItemResponse> items = dashboardMapper.findItems(userId, today, endDate);

        // 오늘부터 7일 뒤까지의 미납 항목을 조회한다.
        // 다음 달에 걸치는 경우 두 달의 결과가 합쳐진 목록이다.
        List<UpcomingPaymentResponse> allPayments = findPayment(userId);

        // 표시 목록을 줄이기 전에 전체 미납 건수를 보관한다.
        int paymentCount = allPayments.size();

        // 납부 예정일이 빠른 순서로 최대 3건을 표시한다.
        // 같은 예정일은 고정 지출 ID 순서로 정렬한다.
        List<UpcomingPaymentResponse> payments = allPayments.stream()
                .sorted(
                        Comparator.comparing(UpcomingPaymentResponse::paymentDate)
                                .thenComparing(UpcomingPaymentResponse::id)
                )
                .limit(3)
                .toList();

        // 소비기한이 지났거나 3일 이내인 보관 중 식재료를 소비기한이 빠른 순으로 최대 3건 조회한다.
        List<DashboardIngredientResponse> ingredients = dashboardMapper.findIngredient(userId, ingredientEndDate);

        // 화면에 표시할 3건과 별도로, 조건에 맞는 전체 식재료 건수를 조회한다.
        int ingredientCount = dashboardMapper.countIngredients(userId, ingredientEndDate);

        // 예정일이 지났거나 7일 이내인 활성 집안일·소모품을 최대 3건 조회한다.
        List<DashboardHouseholdTaskResponse> householdTasks = dashboardMapper.findHouseholdTask(userId, sevenEndDate);

        // 화면에 표시할 3건과 별도로, 같은 조건에 해당하는 전체 건수를 조회한다.
        int householdTaskCount = dashboardMapper.countHouseholdTask(userId, sevenEndDate);

        // 각 기능에서 조회한 결과를 하나의 응답으로 묶는다.
        // 프론트엔드는 이 응답을 받아 대시보드의 요약 카드와 목록을 표시한다.
        return new DashboardResponse(
                today,                          // 조회 기준 날짜
                month,                          // 조회 월
                statistics.totalAmount(),       // 이번 달 실제 지출
                statistics.configured(),        // 예산 등록 여부
                statistics.budgetAmount(),      // 월 예산
                statistics.remainingAmount(),   // 잔여 예산
                statistics.usageRate(),         // 예산 사용률
                recurringAmount,                // 고정 지출 예정 합계
                schedules,                      // 마감이 가까운 일정 목록
                scheduleCount,                  // 마감이 가까운 일정 건수
                items,                          // 보증 종료 예정 물품 목록
                payments,                       // 납부 예정일이 빠른 미납 항목, 최대 3건
                paymentCount,                   // 표시 건수 제한 전 전체 미납 건수
                ingredients,                    // 식재료 목록
                ingredientCount,                // 식재료 건수
                householdTasks,                 // 집안일·소모품 목록
                householdTaskCount              // 집안일·소모품 건수
        );
    }

    /**
     * 오늘부터 7일 뒤까지 납부 예정인 미납 고정 지출을 조회한다.
     * 조회 기간이 다음 달에 걸치면 다음 달 목록도 조회하여 합친다.
     * 두 조회 모두 같은 오늘 날짜를 기준으로 기간을 제한한다.
     */
    @Transactional(readOnly = true)
    public List<UpcomingPaymentResponse> findPayment(Long userId) {
        // 한국 시간 기준 오늘 날짜를 조회 기준으로 사용한다.
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 오늘부터 7일 뒤까지를 조회 범위로 정하고, 종료일이 속한 월도 확인한다.
        LocalDate sevenDaysLater = today.plusDays(7);

        // yyyy-MM 포맷터 생성
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // 오늘 날짜를 포맷에 맞게 문자열로 변환
        String currentMonth = today.format(formatter);

        // 조회 종료일이 속한 월을 YYYY-MM 형식으로 변환한다.
        String nextCurrentMonth = sevenDaysLater.format(formatter);

        // 조회 월을 검증하고 해당 월 1일로 변환한다.
        LocalDate paymentMonth = MonthValidator.parseMonth(currentMonth).atDay(1);

        // 조회 종료일인 7일 뒤가 속한 월의 1일을 구한다.
        LocalDate paymentNextMonth = MonthValidator.parseMonth(nextCurrentMonth).atDay(1);

        // 1. 첫 번째 달 조회
        List<UpcomingPaymentResponse> paymentList = dashboardMapper.findPaymentByUserId(userId, today, paymentMonth);

        // 조회 기간이 월말을 넘어가면 다음 달의 납부 예정 항목도 필요하다.
        // 두 달을 조회하더라도 각 조회의 날짜 범위는 오늘부터 7일 뒤까지로 제한한다.
        if (!paymentMonth.equals(paymentNextMonth)) {
            List<UpcomingPaymentResponse> nextPaymentList = dashboardMapper.findPaymentByUserId(userId, today, paymentNextMonth);
            paymentList.addAll(nextPaymentList); // 리스트 합치기
        }

        return paymentList;

    }


}
