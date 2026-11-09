package com.myhome.statistics;

import java.math.BigDecimal;

/**
 * 통계 화면의 카테고리 한 행에 표시할 이름, 지출 합계, 총지출 대비 비율을 담는다.
 * */
public record CategoryStatisticsResponse(
        Long categoryId,       // 카테고리 ID
        String categoryName,   // 카테고리명
        BigDecimal amount,     // 카테고리별 지출 합계
        BigDecimal percentage  // 전체 지출에서 차지하는 비율(%)
) {
}