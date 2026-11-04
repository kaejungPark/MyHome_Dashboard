package com.myhome.recurringexpense;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 고정 지출 설정과 월별 납부 조회·완료·취소 요청을 처리한다.
 * 사용자 ID는 로그인 정보에서 가져와 본인 소유의 데이터를 처리하도록 한다.
 */
@RestController
@RequestMapping("/api/recurring-expenses")
public class RecurringExpenseController {
    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) { this.recurringExpenseService = recurringExpenseService;}

    /**
     * 로그인한 사용자의 고정 지출 설정을 검색 조건에 맞춰 페이지 단위로 조회한다.
     * active는 null이면 전체, true이면 사용 중, false이면 비활성 항목을 조회한다.
     * 사용 여부와 해당 월의 납부 완료 여부는 서로 다른 값이다.
     */
    @GetMapping
    public ApiResponse<PageResponse<RecurringExpenseResponse>> getRecurringExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(recurringExpenseService.getRecurringExpense(
                loginUser.getId(),
                title,
                categoryId,
                active,
                page,
                size
        ));
    }

    /**
     * 선택한 월의 납부 대상과 납부 상태, 월 전체 예정 금액 합계를 조회한다.
     * month는 YYYY-MM 형식이며, 목록만 페이지 단위로 나눈다.
     * 응답의 items 안에 목록과 페이지 정보가 포함된다.
     */
    @GetMapping("/monthly/{month}")
    public ApiResponse<MonthlyResponse> getMonthlyExpenses(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                recurringExpenseService.getMonthlyExpenses(loginUser.getId(), month, page, size)
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
     * 로그인한 사용자의 고정 지출 설정을 삭제한다.
     * 납부 기록이 있는 항목은 서비스에서 삭제를 차단하고 비활성화를 안내한다.
     * 납부 기록과 연결된 생활비를 제거하려면 납부 취소 기능을 사용해야 한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecurringExpense(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        recurringExpenseService.deleterecurringExpense(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

    /**
     * 고정 지출의 해당 월 납부를 완료 처리하고 성공 시 201을 반환한다.
     * 경로의 month는 납부 대상 월이고, 본문의 paymentDate는 실제 납부일이다.
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
     * 해당 월의 납부 기록과 연결된 생활비를 함께 삭제한다.
     * 고정 지출 설정 자체는 삭제하지 않는다.
     * 사용자 ID는 로그인 정보에서 가져온다.
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
