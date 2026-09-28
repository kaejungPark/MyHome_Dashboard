package com.myhome.homeItem;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.apache.ibatis.annotations.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home-item")
public class HomeItemController {
    private final HomeItemService homeItemService;

    public HomeItemController(HomeItemService homeItemService) {this.homeItemService = homeItemService;}

    /**
     * 현재 로그인 기능이 없기에 파라미터에 userId를 받지 않고 작업 추후 추가 예정 Get, Post
     * GET /api/home-item: 물품 목록 조회
     * */
    @GetMapping
    public ApiResponse<List<HomeItemResponse>> getHomeItem () {
        return ApiResponse.success(homeItemService.getHomeItem());
    }

    /**
     * POST /api/home-item: 물품 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createHomeItem(@Valid @RequestBody HomeItemSaveRequest request) {
        homeItemService.createHomeItem(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/home-item/{id}: 물품 수정
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateHomeItem(@PathVariable ("id") Long id, @Valid @RequestBody HomeItemSaveRequest request) {
        homeItemService.updateHomeItem(id, request);

        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/home-item/{id}: 물품 수정
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteHomeItem(@PathVariable ("id") Long id) {
        homeItemService.deleteHomeItem(id);

        return ApiResponse.success(null);
    }
}
