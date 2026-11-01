package com.myhome.homeItem;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 물품 조회·등록·수정·삭제 요청을 받아 서비스에 전달한다.
 * 사용자 ID는 로그인 정보에서 가져와 사용자별 물품을 구분한다.
 */
@RestController
@RequestMapping("/api/home-item")
public class HomeItemController {
    private final HomeItemService homeItemService;

    public HomeItemController(HomeItemService homeItemService) {this.homeItemService = homeItemService;}

    /**
     * 로그인한 사용자의 물품을 검색 조건에 맞춰 페이지 단위로 조회한다.
     * 물품명·분류·상태가 비어 있으면 해당 검색 조건을 적용하지 않는다.
     * 조회한 목록과 전체 건수, 페이지 정보를 함께 반환한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<HomeItemResponse>> getHomeItem (
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(homeItemService.getHomeItem(
                loginUser.getId(),
                name,
                category,
                status,
                page,
                size
        ));
    }

    /**
     * 로그인한 사용자의 물품을 등록한다.
     * @Valid로 필수값과 입력 형식을 검사하고, 등록 성공 시 201 상태를 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createHomeItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody HomeItemSaveRequest request) {
        homeItemService.createHomeItem(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * URL로 전달받은 ID의 물품 정보를 수정한다.
     * 로그인한 사용자의 ID도 함께 전달하여 본인 소유의 물품만 수정하도록 한다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateHomeItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody HomeItemSaveRequest request) {
        homeItemService.updateHomeItem(loginUser.getId(), id, request);

        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자가 소유한 물품을 삭제한다.
     * 처분 상태로 변경하는 것과 달리 DB에서 해당 물품을 제거한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteHomeItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id) {
        homeItemService.deleteHomeItem(loginUser.getId(), id);

        return ApiResponse.success(null);
    }
}
