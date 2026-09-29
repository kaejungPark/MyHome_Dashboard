package com.myhome.statistics;

import java.math.BigDecimal;
import java.util.List;

/**
 * StatisticsResponse — 전체 통계 응답
 * */
public record StatisticsResponse (
    String month, // 조회 월
    BigDecimal totalAmount, // 총지출
    boolean configured, // 예산 설정 여부
    BigDecimal budgetAmount, // 예산 금액, 미설정 시 null
    BigDecimal remainingAmount, // 잔여 예산, 미설정 시 null
    BigDecimal usageRate, // 예산 사용률, 예산 미설정·0원이면 null
    List<CategoryStatisticsResponse> categories
){

}
