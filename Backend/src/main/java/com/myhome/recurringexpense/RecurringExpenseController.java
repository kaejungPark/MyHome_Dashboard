package com.myhome.recurringexpense;

import java.util.List;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recurring-expenses")
public class RecurringExpenseController {
    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) { this.recurringExpenseService = recurringExpenseService;}

    /** 현재 로그인 기능이 없기에 파라미터에 userId를 받지 않고 작업 추후 추가 예정 Get, Post  */
    // GET /api/recurringExpense: 고정 지출 목록 조회
    @GetMapping
    public ApiResponse<List<RecurringExpenseResponse>> getRecurringExpense() {
        return ApiResponse.success(recurringExpenseService.getRecurringExpense());
    }

    /**
     * GET /api/recurring-expenses/monthly/2026-09
     * 선택한 월의 고정 지출 납부 예정 목록과 합계를 조회한다.
     */
    @GetMapping("/monthly/{month}")
    public ApiResponse<MonthlyResponse> getMonthlyExpenses(
            @PathVariable("month") String month
    ) {
        return ApiResponse.success(
                recurringExpenseService.getMonthlyExpenses(month)
        );
    }

    /**
     * POST /api/recurringExpense: 고정 지출 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createRecurringExpense(@Valid @RequestBody RecurringExpenseSaveRequest request) {
        recurringExpenseService.createRecurringExpense(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/recurringExpense/{id}: 고정 지출 수정
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateRecurringExpense (@PathVariable("id") Long id, @Valid @RequestBody RecurringExpenseSaveRequest request) {
        recurringExpenseService.updateRecurringExpense(id, request);
        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/recurringExpense/{id}: 고정 지출 수정
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecurringExpense(@PathVariable("id") Long id) {
        recurringExpenseService.deleterecurringExpense(id);
        return ApiResponse.success(null);
    }
}
