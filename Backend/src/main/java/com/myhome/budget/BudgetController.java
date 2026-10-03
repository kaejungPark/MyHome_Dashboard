package com.myhome.budget;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    // GET /api/budgets/2026-09: 해당 월의 예산과 지출 현황 조회
    @GetMapping("/{month}")
    public ApiResponse<BudgetResponse> getBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month) {
        return ApiResponse.success(budgetService.getBudget(loginUser.getId(), month));
    }

    /**
     * POST /api/budgets/{month}: 예산 등록
     */
    @PostMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> createBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month,
            @Valid @RequestBody BudgetCreateRequest request) {
        budgetService.createBudget(loginUser.getId(), month, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>success(null));
    }

    /**
     * PUT /api/budgets/{month}: 예산 수정
     */
    @PutMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> updateBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month,
            @Valid @RequestBody BudgetUpdateRequest request) {
        budgetService.updateBudget(loginUser.getId(), month, request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    /**
     * DELETE /api/budgets/{month}: 예산 삭제
     */
    @DeleteMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> deleteBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month) {
        budgetService.deleteBudget(loginUser.getId(), month);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }
}
