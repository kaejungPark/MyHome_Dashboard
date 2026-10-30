package com.myhome.dashborad;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 사용자의 대시보드 조회 요청을 받는다.
 * 로그인 정보의 사용자 ID를 서비스에 전달하고,
 * 생활비와 관리 예정 항목을 하나의 응답으로 반환한다.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController (DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 로그인한 사용자의 대시보드 정보를 조회한다.
     * 조회 기준 날짜와 월은 서비스에서 결정한다.
     */
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard (@AuthenticationPrincipal LoginUser loginUser) {
        // 화면에서 사용자 ID를 받지 않고 로그인 정보의 ID로 조회한다.
        // 조회 날짜와 월은 서비스에서 한국 시간 기준으로 결정한다.
        return ApiResponse.success(dashboardService.findDashboards(loginUser.getId()));
    }
}
