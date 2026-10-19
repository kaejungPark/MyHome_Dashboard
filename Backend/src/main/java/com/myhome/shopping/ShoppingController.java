package com.myhome.shopping;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 로그인한 사용자의 장보기 목록 조회와 등록·수정·삭제 API를 제공한다.
 * 사용자 ID는 요청값 대신 로그인 인증 정보에서 가져온다.
 */
@RestController
@RequestMapping("/api/shopping-items")
public class ShoppingController {
    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    /**
     * 물품명·구매 여부·날짜 조건으로 로그인한 사용자의 장보기 목록을 조회한다.
     * 조회 결과는 전체 건수와 페이지 정보를 포함하여 반환한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ShoppingResponse>> getShoppings (
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(required = false) Boolean isCompleted,
            @RequestParam(defaultValue = "") String purchaseDueDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(shoppingService.getShoppings(
            loginUser.getId(),
            name,
            isCompleted,
            purchaseDueDate,
            page,
            size
        ));
    }

    /**
     * 입력값을 검증하고 로그인한 사용자의 장보기 항목을 등록한다.
     * 최초 등록 시 미구매 상태로 저장하며, 성공하면 HTTP 201을 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createShopping (
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody ShoppingSaveRequest request
    ) {
        shoppingService.createShopping(loginUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * 로그인한 사용자 소유의 장보기 항목을 수정한다.
     * 구매 여부와 실제 구매일은 일반 수정에서 변경하지 않는다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateShopping (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody ShoppingSaveRequest request
    ) {
        shoppingService.updateShopping(loginUser.getId(), id, request);
        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자 소유의 장보기 항목을 삭제한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteShopping (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        shoppingService.deleteShopping(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자 소유의 장보기 항목을 구매 완료 처리한다.
     * 실제 구매일은 서버에서 결정하므로 요청 본문을 받지 않는다.
     */
    @PutMapping("/{id}/completed")
    public ApiResponse<Void> completedShopping (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        shoppingService.completedShopping(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자 소유의 장보기 항목을 완료 취소한다.
     * 미구매 상태로 되돌리고 실제 구매일을 초기화한다.
     */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancelShopping (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        shoppingService.cancelShopping(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

    /**
     * 발송 가능 여부와 재시도 판단에 필요한 알림 이력 정보를 담는다.
     * updatedDt는 DB에 저장된 UTC 시각이다.
     */
    public static record ShoppingNotificationHistory (
            Long id,                  // 발송 이력 ID
            String status,            // PROCESSING, SENT, FAILED
            Integer attemptCount,     // 현재까지의 발송 시도 횟수
            LocalDateTime updatedDt   // 마지막 상태 변경 시각
    ){
    }
}
