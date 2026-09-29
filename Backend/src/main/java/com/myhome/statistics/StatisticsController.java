package com.myhome.statistics;

import com.myhome.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService ){ this.statisticsService = statisticsService; }

    @GetMapping("/{month}")
    public ApiResponse<StatisticsResponse> getStatistics(@PathVariable ("month") String month) {
        return ApiResponse.success(statisticsService.findStatistics(month));
    }
}
