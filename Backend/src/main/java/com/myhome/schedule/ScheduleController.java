package com.myhome.schedule;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) { this.scheduleService = scheduleService;}

    /** 현재 로그인 기능이 없기에 파라미터에 userId를 받지 않고 작업 추후 추가 예정 Get, Post  */
    // GET /api/schedules: 사용자별 일정 목록 조회
    @GetMapping
    public ApiResponse<List<ScheduleResponse>> getSchedule() {
        return ApiResponse.success(scheduleService.getSchedul());
    }

    /**
     * POST /api/schedules: 일정 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createSchedule(@Valid @RequestBody ScheduleSaveRequest request) {
        scheduleService.createSchedule(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/schedules/{id}: 일정 수정
     * */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateSchedule(@PathVariable("id") Long id, @Valid @RequestBody ScheduleSaveRequest request) {
        scheduleService.updateSchedule(id, request);

        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/schedules/{id}: 일정 삭제
     * */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSchedule(@PathVariable("id") Long id) {
        scheduleService.deleteSchedule(id);

        return ApiResponse.success(null);
    }
}
