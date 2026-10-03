package com.myhome.statistics;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService ){ this.statisticsService = statisticsService; }

    /**
     * 로그인한 사용자의 요청 월 통계를 조회한다.
     * 사용자 ID는 인증 정보에서, 조회 월은 URL에서 가져온다.
     */
    @GetMapping("/{month}")
    public ApiResponse<StatisticsResponse> getStatistics(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("month") String month) {
        /**
         * 전달받은 사용자의 월별 지출을 카테고리별로 집계하고
         * 해당 월의 예산 정보를 함께 반환한다.
         * 지출과 예산 조회에 동일한 사용자 ID를 적용한다.
         */
        return ApiResponse.success(statisticsService.findStatistics(loginUser.getId(), month));
    }
}
