package com.myhome.recurringexpense;

import com.myhome.common.response.PageResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 조회 월, 월 전체 예정 금액과 페이지 단위 납부 목록을 반환한다.
 * totalAmount는 현재 페이지 합계나 미납 잔액이 아니다.
 * items는 단순 목록이 아니라 목록과 페이지 정보를 담은 PageResponse다.
 */
public record  MonthlyResponse(
        String month,                           // 조회 월: YYYY-MM
        BigDecimal totalAmount,                 // 납부 예정 금액 합계
        PageResponse<MonthlyPaymentItem> items  // 월별 납부 목록과 페이지 정보
) {

}
