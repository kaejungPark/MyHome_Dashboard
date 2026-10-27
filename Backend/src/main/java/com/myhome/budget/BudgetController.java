package com.myhome.budget;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 로그인한 사용자의 월별 예산 조회·등록·수정·삭제 요청을 받는다.
 * 사용자 ID는 로그인 정보에서 가져오고, 대상 월은 URL에서 받는다.
 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /**
     * 선택한 월의 예산과 실제 지출 합계를 조회한다.
     * 예산을 등록하지 않은 월도 지출 현황을 확인할 수 있다.
     * month는 2026-10과 같은 연도-월 형식이다.
     */
    @GetMapping("/{month}")
    public ApiResponse<BudgetResponse> getBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month) {
        return ApiResponse.success(budgetService.getBudget(loginUser.getId(), month));
    }

    /**
     * 선택한 월의 예산을 등록한다.
     * @Valid로 금액을 검사하고 정상 등록 시 HTTP 201을 반환한다.
     * 같은 사용자·월에 이미 예산이 있으면 서비스에서 중복 오류를 반환한다.
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
     * 선택한 월에 등록된 예산 금액을 수정한다.
     * 금액만 변경하며 실제 지출 내역에는 영향을 주지 않는다.
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
     * 선택한 월의 예산 설정을 삭제한다.
     * 해당 월의 실제 지출 내역은 그대로 유지한다.
     */
    @DeleteMapping("/{month}")
    public ResponseEntity<ApiResponse<Void>> deleteBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month) {
        budgetService.deleteBudget(loginUser.getId(), month);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }
}
