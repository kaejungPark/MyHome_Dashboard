package com.myhome.recurringexpense;

import java.math.BigDecimal;

/**
 * 고정 지출 관리 목록과 수정 폼에서 사용하는 설정 정보다.
 * paymentDay는 매월 반복되는 납부일이며, 특정 월의 실제 날짜는 아니다.
 * active는 설정의 사용 여부로, 월별 납부 완료 여부와는 별개다.
 */
public record RecurringExpenseResponse(
        Long id,                 // 고정 지출 ID
        Long categoryId,         // 카테고리 ID
        String categoryName,     // 카테고리명
        String title,            // 항목명
        BigDecimal amount,       // 매월 납부 예정 금액
        Integer paymentDay,      // 매월 납부일
        String paymentMethod,    // 결제 수단
        String startMonth,       // 시작 월: YYYY-MM
        String endMonth,         // 종료 월: 없으면 null
        boolean active,          // 사용 여부
        String memo              // 메모
) {
}