package com.myhome.schedule;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) { this.scheduleService = scheduleService;}

    /**
     * GET /api/schedules: 사용자별 일정 목록 조회
     * */
    @GetMapping
    public ApiResponse<List<ScheduleResponse>> getSchedule(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(scheduleService.getSchedul(loginUser.getId()));
    }

    /**
     * POST /api/schedules: 일정 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody ScheduleSaveRequest request) {
        scheduleService.createSchedule(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/schedules/{id}: 일정 수정
     * */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @Valid @RequestBody ScheduleSaveRequest request) {
        scheduleService.updateSchedule(loginUser.getId(), id, request);

        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/schedules/{id}: 일정 삭제
     * */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        scheduleService.deleteSchedule(loginUser.getId(), id);

        return ApiResponse.success(null);
    }
}
