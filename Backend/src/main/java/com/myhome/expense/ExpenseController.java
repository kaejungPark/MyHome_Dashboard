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
 * 생활비 조회·등록·수정·삭제 요청을 받아 서비스에 전달한다.
 * 사용자 ID는 요청값이 아닌 로그인 정보에서 가져온다.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    /**
     * 로그인한 사용자의 생활비를 검색 조건에 맞춰 페이지 단위로 조회한다.
     * 제목·카테고리·지출 유형을 선택하지 않으면 해당 조건으로 제한하지 않는다.
     * 목록과 함께 전체 건수, 전체 페이지 수를 반환한다.
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
     * 로그인한 사용자의 생활비를 등록한다.
     * @Valid로 요청값을 검사하고, 등록 성공 시 201 상태를 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody ExpenseCreateRequest request) {
        expenseService.createExpense(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Void>success(null));
    }

    /**
     * URL에 전달된 ID의 생활비를 수정한다.
     * 본인 소유 여부와 고정 지출 납부 기록 연결 여부는 서비스와 매퍼에서 확인한다.
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
     * URL에 전달된 ID의 생활비를 삭제한다.
     * 사용자 ID를 함께 전달하여 다른 사용자의 생활비가 삭제되지 않도록 한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        expenseService.deleteExpense(loginUser.getId(),id);
        return ApiResponse.<Void>success(null);
    }
}