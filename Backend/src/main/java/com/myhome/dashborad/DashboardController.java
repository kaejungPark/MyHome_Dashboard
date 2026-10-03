package com.myhome.dashborad;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        /**
         * 한국 시간의 오늘을 기준으로 사용자의 생활 정보를 모아 반환한다.
         * 이번 달 통계와 고정 지출 예정 금액,
         * 오늘부터 30일 이내의 미완료 일정과 보증 종료 예정 물품을 조회한다.
         * 다른 서비스를 호출할 때도 같은 사용자 ID를 전달한다.
         */
        return ApiResponse.success(dashboardService.findDashboards(loginUser.getId()));
    }
}
