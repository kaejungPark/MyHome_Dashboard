package com.myhome.expense;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 지출 조회·등록·수정·삭제” 요청을 받아 Service에 전달한다.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }




    /**
     * 로그인한 사용자의 지출 목록을 페이지 단위로 조회한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ExpenseResponse>> getExpenses(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "") String expenseType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(expenseService.getExpenses(
                loginUser.getId(),
                title,
                categoryId,
                expenseType,
                page,
                size
        ));
    }

    /**
     * POST /api/expenses: 지출 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody ExpenseCreateRequest request) {
        expenseService.createExpense(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Void>success(null));
    }

    /**
     * PUT /api/expenses/{id}: 지출 수정
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @Valid @RequestBody ExpenseUpdateRequest request) {
        expenseService.updateExpense(loginUser.getId(), id, request);
        return ApiResponse.<Void>success(null);
    }

    /**
     * DELETE /api/expenses/{id}: 지출 삭제
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        expenseService.deleteExpense(loginUser.getId(),id);
        return ApiResponse.<Void>success(null);
    }
}