package com.myhome.admin;

import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import org.springframework.web.bind.annotation.*;
import com.myhome.auth.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/admin/community/reports")
public class AdminCommunityController {
    private final AdminCommunityService adminCommunityService;

    public AdminCommunityController (AdminCommunityService adminCommunityService) {
        this.adminCommunityService = adminCommunityService;
    }

    /**
     * 관리자용 신고 목록을 상태별로 페이지 조회한다.
     * 기본값은 처리 대기 신고이며, ALL이면 모든 상태를 조회한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminReportListResponse>> getReports (
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "PENDING") String status
    ) {
        return ApiResponse.success(adminCommunityService.getReports(page, size, status));
    }

    /**
     * 신고 ID로 신고 내용, 대상 게시글과 처리 이력을 조회한다.
     * 숨김 게시글과 처리 완료된 신고도 조회할 수 있다.
     */
    @GetMapping("/{id}")
    public ApiResponse<AdminReportDetailResponse> getReport(@PathVariable("id") Long id) {
        return ApiResponse.success(adminCommunityService.getReport(id));
    }

    /**
     * 처리 대기 신고를 수용하거나 기각한다.
     * 처리자 ID는 요청 본문이 아닌 로그인한 관리자 정보에서 가져온다.
     */
    @PatchMapping("/{id}")
    public ApiResponse<Void> processReport(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminReportProcessRequest request
    ) {
        adminCommunityService.processReport(loginUser.getId(), id, request);
        return ApiResponse.success(null);
    }
}
