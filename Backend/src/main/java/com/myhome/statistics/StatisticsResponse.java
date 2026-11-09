package com.myhome.statistics;

import java.math.BigDecimal;
import java.util.List;

/**
 * 선택 월의 지출·예산 요약, 전월 대비 증감, 카테고리별 내역을 한 번의 응답으로 전달한다.
 * */
public record StatisticsResponse (
    String month, // 조회 월
    BigDecimal totalAmount, // 총지출
    boolean configured, // 예산 설정 여부
    BigDecimal budgetAmount, // 예산 금액, 미설정 시 null
    BigDecimal remainingAmount, // 잔여 예산, 미설정 시 null
    BigDecimal usageRate, // 예산 사용률, 예산 미설정·0원이면 null
    List<CategoryStatisticsResponse> categories, // 카테고리별 통계
    BigDecimal previousMonthAmount, // 전월 실제 지출 합계
    BigDecimal changeAmount,        // 선택 월 - 전월: 양수는 증가, 음수는 감소
    BigDecimal changeRate           // 전월 대비 증감률(%), 전월 지출이 0이면 null
){

}
