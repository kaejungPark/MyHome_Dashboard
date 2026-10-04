package com.myhome.recurringexpense;

import java.util.List;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recurring-expenses")
public class RecurringExpenseController {
    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) { this.recurringExpenseService = recurringExpenseService;}

    /**
     * GET /api/recurring-expenses: 현재 사용자의 고정 지출 관리 목록을 조회한다.
     * */
    @GetMapping
    public ApiResponse<List<RecurringExpenseResponse>> getRecurringExpense(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(recurringExpenseService.getRecurringExpense(loginUser.getId()));
    }

    /**
     * GET /api/recurring-expenses/monthly/{xxxx-xx}
     * 선택한 월의 고정 지출 납부 예정 목록과 합계를 조회한다.
     */
    @GetMapping("/monthly/{month}")
    public ApiResponse<MonthlyResponse> getMonthlyExpenses(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month
    ) {
        return ApiResponse.success(
                recurringExpenseService.getMonthlyExpenses(loginUser.getId(), month)
        );
    }

    /**
     * POST /api/recurring-expenses: 고정 지출을 등록한다.
     * JSON 본문을 검증하고 등록 성공 시 201을 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createRecurringExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody RecurringExpenseSaveRequest request) {
        recurringExpenseService.createRecurringExpense(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/recurring-expenses/{id}: 고정 지출을 수정한다.
     * 대상 ID는 URL에서, 변경할 값은 JSON 본문에서 받는다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateRecurringExpense (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @Valid @RequestBody RecurringExpenseSaveRequest request) {
        recurringExpenseService.updateRecurringExpense(loginUser.getId(), id, request);
        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/recurring-expenses/{id}: 고정 지출 설정을 삭제한다.
     * 실제 지출 내역은 삭제하지 않는다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecurringExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        recurringExpenseService.deleterecurringExpense(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

    /**
     * 해당 월의 고정 지출을 납부 완료 처리한다.
     * 사용자 ID는 로그인 정보에서 가져온다.
     */
    @PostMapping("/{id}/payments/{month}")
    public ResponseEntity<ApiResponse<Void>> completePayment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @PathVariable("month") String month,
            @Valid @RequestBody RecurringPaymentRequest request
    ) {
        recurringExpenseService.completePayment(
                loginUser.getId(),
                id,
                month,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>success(null));
    }

    /**
     * 해당 월의 납부를 취소한다.
     * 사용자 ID는 요청 본문이 아닌 로그인 정보에서 가져온다.
     */
    @DeleteMapping("/{id}/payments/{month}")
    public ApiResponse<Void> cancelPayment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @PathVariable("month") String month
    ) {
        recurringExpenseService.cancelPayment(
                loginUser.getId(), id, month
        );

        return ApiResponse.success(null);
    }
}
