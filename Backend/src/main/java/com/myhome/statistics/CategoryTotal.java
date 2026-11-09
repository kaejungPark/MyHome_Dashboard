package com.myhome.statistics;

import java.math.BigDecimal;

/**
 * 매퍼가 DB에서 집계한 카테고리별 지출 합계다. 서비스는 이 금액으로 총지출과 카테고리별 비율을 계산한다.
 * */
public record CategoryTotal (
       Long categoryId, // 카테고리 ID
       String categoryName, // 카테고리명
       BigDecimal amount // 해당 카테고리 지출 합계
){
}
