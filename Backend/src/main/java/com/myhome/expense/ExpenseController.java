package com.myhome.expense;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

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
     * 로그인한 사용자의 지출 목록을 조회한다.
     * 사용자 ID는 요청값이 아닌 서버의 인증 정보에서 가져온다.
     */
    @GetMapping
    public ApiResponse<List<ExpenseResponse>> getExpenses(@AuthenticationPrincipal LoginUser loginUser) {

        return ApiResponse.success(expenseService.getExpenses(loginUser.getId()));
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