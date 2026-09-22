package com.myhome.budget;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /** 현재 로그인 기능이 없기에 파라미터에 userId를 받지 않고 작업 추후 추가 예정 Get, Post  */

    // GET /api/budgets/2026-09: 해당 월의 예산과 지출 현황 조회
    @GetMapping("/{month}")
    public ApiResponse<BudgetResponse> getBudget(@PathVariable("month") String month) {
        return ApiResponse.success(budgetService.getBudget(month));
    }

    /**
     * POST /api/budget: 예산 등록
     */
    @PostMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> createBudget(@PathVariable("month") String month,@Valid @RequestBody BudgetCreateRequest request) {
        budgetService.createBudget(month, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>success(null));
    }

    /**
     * PUT /api/budget/{id}: 예산 수정
     */
    @PutMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> updateBudget(@PathVariable("month") String month,@Valid @RequestBody BudgetUpdateRequest request) {
        budgetService.updateBudget(month, request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    /**
     * DELETE /api/budget/{id}: 예산 삭제
     */
    @DeleteMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> deleteBudget(@PathVariable("month") String month) {
        budgetService.deleteBudget(month);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }
}
