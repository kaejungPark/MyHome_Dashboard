package com.myhome.admin;

import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 숨김 게시글 조회와 숨김 해제를 제공한다.
 * 관리자 접근 권한은 SecurityConfig에서 검사한다.
 */
@RestController
@RequestMapping("/api/admin/community/hidden")
public class AdminHiddenCommunityController {

    private final AdminHiddenCommunityService adminHiddenCommunityService;

    public AdminHiddenCommunityController(
            AdminHiddenCommunityService adminHiddenCommunityService
    ) {
        this.adminHiddenCommunityService = adminHiddenCommunityService;
    }

    /**
     * GET /api/admin/community/hidden:
     * 숨김 게시글 목록을 페이지 조회한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminHiddenCommunityListResponse>>
    getHiddenCommunities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                adminHiddenCommunityService.getHiddenCommunities(page, size)
        );
    }

    /**
     * GET /api/admin/community/hidden/{id}:
     * 숨김 게시글의 본문과 숨김 정보를 조회한다.
     */
    @GetMapping("/{id}")
    public ApiResponse<AdminHiddenCommunityDetailResponse> getHiddenCommunity(
            @PathVariable("id") Long id
    ) {
        return ApiResponse.success(
                adminHiddenCommunityService.getHiddenCommunity(id)
        );
    }

    /**
     * PATCH /api/admin/community/hidden/{id}/restore:
     * 게시글을 다시 공개한다. 요청 본문은 받지 않는다.
     */
    @PatchMapping("/{id}/restore")
    public ApiResponse<Void> restoreCommunity(
            @PathVariable("id") Long id
    ) {
        adminHiddenCommunityService.restoreCommunity(id);
        return ApiResponse.success(null);
    }
}