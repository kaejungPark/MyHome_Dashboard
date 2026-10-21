package com.myhome.admin;

import com.myhome.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * GET /api/admin/dashboard: 관리자 대시보드의 기본 통계를 조회한다.
     * 관리자 접근 권한은 SecurityConfig에서 검사한다.
     */
    @GetMapping
    public ApiResponse<AdminDashboardResponse> getAdminDashboard() {
        return ApiResponse.success(adminDashboardService.findAdminDashboard());
    }

    /**
     * GET /api/admin/dashboard/signup-trend:
     * 오늘을 포함한 최근 7일의 날짜별 가입자 수를 조회한다.
     * 가입자가 없는 날도 포함하며, 오래된 날짜부터 반환한다.
     */
    @GetMapping("/signup-trend")
    public ApiResponse<List<AdminSignupTrendResponse>> getSignupTrend() {
        return ApiResponse.success(adminDashboardService.findSignupTrend());
    }
}
