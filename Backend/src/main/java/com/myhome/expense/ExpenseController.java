package com.myhome.expense;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

/**
 * 지출 조회·등록 요청을 받아 Service에 전달한다.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // GET /api/expenses: 사용자별 지출 목록 조회
    @GetMapping
    public ApiResponse<List<ExpenseResponse>> getExpenses() {
        return ApiResponse.success(expenseService.getExpenses());
    }

    /**
     * POST /api/expenses: 지출 등록
     * @Valid로 요청값을 검증하고, 등록 성공 시 HTTP 201을 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createExpense(
            @Valid @RequestBody ExpenseCreateRequest request
    ) {
        expenseService.createExpense(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>success(null));
    }

    /**
     * PUT /api/expenses/{id}: 지출 수정
     * 요청 본문을 검증하고 성공 시 HTTP 200을 반환한다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateExpense(
            @PathVariable("id") Long id,
            @Valid @RequestBody ExpenseUpdateRequest request
    ) {
        expenseService.updateExpense(id, request);
        return ApiResponse.<Void>success(null);
    }

    /**
     * DELETE /api/expenses/{id}: 지출 삭제
     * 성공 시 HTTP 200과 공통 응답을 반환한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExpense(
            @PathVariable("id") Long id
    ) {
        expenseService.deleteExpense(id);
        return ApiResponse.<Void>success(null);
    }
}