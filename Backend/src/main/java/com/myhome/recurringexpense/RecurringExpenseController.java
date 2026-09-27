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

    /** 현재 로그인 기능이 없기에 파라미터에 userId를 받지 않고 작업 추후 추가 예정 Get, Post
    GET /api/recurring-expenses: 현재 사용자의 고정 지출 관리 목록을 조회한다. */
    @GetMapping
    public ApiResponse<List<RecurringExpenseResponse>> getRecurringExpense() {
        return ApiResponse.success(recurringExpenseService.getRecurringExpense());
    }

    /**
     * GET /api/recurring-expenses/monthly/{xxxx-xx}
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
     * POST /api/recurring-expenses: 고정 지출을 등록한다.
     * JSON 본문을 검증하고 등록 성공 시 201을 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createRecurringExpense(@Valid @RequestBody RecurringExpenseSaveRequest request) {
        recurringExpenseService.createRecurringExpense(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/recurring-expenses/{id}: 고정 지출을 수정한다.
     * 대상 ID는 URL에서, 변경할 값은 JSON 본문에서 받는다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateRecurringExpense (@PathVariable("id") Long id, @Valid @RequestBody RecurringExpenseSaveRequest request) {
        recurringExpenseService.updateRecurringExpense(id, request);
        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/recurring-expenses/{id}: 고정 지출 설정을 삭제한다.
     * 실제 지출 내역은 삭제하지 않는다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecurringExpense(@PathVariable("id") Long id) {
        recurringExpenseService.deleterecurringExpense(id);
        return ApiResponse.success(null);
    }
}
