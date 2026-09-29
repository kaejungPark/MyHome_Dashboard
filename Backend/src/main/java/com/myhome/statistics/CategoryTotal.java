package com.myhome.statistics;

import java.math.BigDecimal;

/**
 * CategoryTotal — Mapper 조회 결과
 * */
public record CategoryTotal (
       Long categoryId, // 카테고리 ID
       String categoryName, // 카테고리명
       BigDecimal amount // 해당 카테고리 지출 합계
){
}
