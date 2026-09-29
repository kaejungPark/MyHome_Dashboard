package com.myhome.statistics;

import java.math.BigDecimal;

/**
 * CategoryStatisticsResponse — 화면에 보낼 카테고리별 통계
 * */
public record CategoryStatisticsResponse(
        Long categoryId,       // 카테고리 ID
        String categoryName,   // 카테고리명
        BigDecimal amount,     // 카테고리별 지출 합계
        BigDecimal percentage  // 전체 지출에서 차지하는 비율(%)
) {
}