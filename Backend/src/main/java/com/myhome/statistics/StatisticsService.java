package com.myhome.statistics;

import com.myhome.budget.BudgetMapper;
import com.myhome.common.validation.MonthValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class StatisticsService {

    private final StatisticsMapper statisticsMapper;
    private final BudgetMapper budgetMapper;

    public StatisticsService (
            StatisticsMapper statisticsMapper,
            BudgetMapper budgetMapper)
    {
        this.statisticsMapper = statisticsMapper;
        this.budgetMapper = budgetMapper;
    }

    @Transactional(readOnly = true)
    public StatisticsResponse findStatistics(Long userId, String month) {

        // 조회 월을 검증하고 해당 월 1일로 변환한다.
        LocalDate monthStart = MonthValidator.parseMonth(month).atDay(1);

        // 다음 달 1일을 계산한다. 12월이면 자동으로 다음 해 1월이 된다.
        LocalDate nextMonthStart = monthStart.plusMonths(1);

        // 현재 사용자의 조회 월 지출을 카테고리별로 합산해 조회한다.
        List<CategoryTotal> categoryTotals = statisticsMapper.findCategoryTotal(userId, monthStart, nextMonthStart);

        // 현재 사용자의 조회 월 예산을 조회한다. 미등록이면 null을 반환한다.
        BigDecimal budget = budgetMapper.findAmount(userId, monthStart);

        // 전체 지출 합계(totalAmount) 계산
        BigDecimal totalAmount = categoryTotals.stream().map(CategoryTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 카테고리별 비율(percentage)을 갖는 CategoryStatisticsResponse 리스트 생성
        List<CategoryStatisticsResponse> responses = categoryTotals.stream().map(
                category -> {
                    BigDecimal percentage = BigDecimal.ZERO;

                    // 전체 합계가 0보다 클 때만 비율 계산 (0으로 나누기 예외 방지)
                    if(totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                        percentage = category.amount()
                                .multiply(new BigDecimal("100"))
                                .divide(totalAmount, 2, RoundingMode.HALF_UP); // 소수점 둘째 자리까지 반올림
                    }
                    return new CategoryStatisticsResponse(
                            category.categoryId(),
                            category.categoryName(),
                            category.amount(),
                            percentage
                    );
                }
        ).toList();

        // 예산 등록 여부와 잔여 예산, 예산 사용률을 계산한다.
        boolean configured = budget != null;

        // 예산이 없으면 null, 지출이 예산을 초과하면 음수로 표시한다.
        BigDecimal remainingAmount = configured
                ? budget.subtract(totalAmount)
                : null;

        // 예산이 없거나 0원이면 사용률을 계산하지 않는다.
        BigDecimal usageRate = null;
        if (configured && budget.compareTo(BigDecimal.ZERO) > 0) {
            usageRate = totalAmount
                    .multiply(new BigDecimal("100"))
                    .divide(budget, 2, RoundingMode.HALF_UP);
        }

        // 월별 통계와 카테고리별 통계를 하나의 응답으로 반환한다.
        return new StatisticsResponse(
                month,
                totalAmount,
                configured,
                budget,
                remainingAmount,
                usageRate,
                responses
        );
    }
}
