package com.myhome.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 생활비 목록과 수정 폼에 사용할 조회 결과다.
 * 카테고리 ID는 수정 시 항목 선택에, 카테고리명은 목록 표시에 사용한다.
 * 서버에서 금액을 정확하게 다루기 위해 BigDecimal을 사용한다.
 */
public record ExpenseResponse(
        Long id,                 // 지출 ID
        Long categoryId,         // 카테고리 ID
        String categoryName,     // 카테고리명
        BigDecimal amount,       // 지출 금액
        LocalDate expenseDate,   // 지출일
        String expenseType,      // 지출 유형: FIXED(고정 지출), VARIABLE(변동 지출)
        String paymentMethod,    // 결제 수단
        String title,            // 지출 제목
        String memo              // 메모
) {
}