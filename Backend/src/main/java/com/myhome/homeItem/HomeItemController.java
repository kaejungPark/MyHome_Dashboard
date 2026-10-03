package com.myhome.homeItem;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.apache.ibatis.annotations.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home-item")
public class HomeItemController {
    private final HomeItemService homeItemService;

    public HomeItemController(HomeItemService homeItemService) {this.homeItemService = homeItemService;}

    /**
     * GET /api/home-item: 물품 목록 조회
     * */
    @GetMapping
    public ApiResponse<List<HomeItemResponse>> getHomeItem (@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(homeItemService.getHomeItem(loginUser.getId()));
    }

    /**
     * POST /api/home-item: 물품 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createHomeItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody HomeItemSaveRequest request) {
        homeItemService.createHomeItem(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/home-item/{id}: 물품 수정
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
     * DELETE /api/home-item/{id}: 물품 삭제
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteHomeItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id) {
        homeItemService.deleteHomeItem(loginUser.getId(), id);

        return ApiResponse.success(null);
    }
}
