package com.myhome.schedule;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) { this.scheduleService = scheduleService;}

    /**
     * 로그인한 사용자의 일정 중 조회 월과 기간이 하루라도 겹치는 일정을 조회한다.
     * 조회 월은 YYYY-MM 형식이며, 이전 달에 시작해 조회 월까지 이어지는 일정도 포함한다.
     * 요청한 페이지의 일정 목록과 해당 월의 전체 일정 건수, 페이지 정보를 함께 반환한다.
     */
    @GetMapping("/{month}")
    public ApiResponse<PageResponse<ScheduleResponse>> getSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("month") String month,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                scheduleService.getSchedul(loginUser.getId(), month, page, size)
        );
    }

    /**
     * 입력값을 검증한 뒤 로그인한 사용자의 일정을 등록한다.
     * 일정의 사용자 ID는 로그인 정보에서 가져오며, 등록에 성공하면 201 Created를 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody ScheduleSaveRequest request) {
        scheduleService.createSchedule(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * PUT /api/schedules/{id}로 로그인한 사용자의 일정을 수정한다.
     * URL의 id는 수정할 일정 번호이고, 사용자 ID는 로그인 정보에서 가져온다.
     * 완료 여부도 제목, 마감일 등과 함께 요청 본문으로 받아 저장한다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id,
            @Valid @RequestBody ScheduleSaveRequest request) {
        scheduleService.updateSchedule(loginUser.getId(), id, request);

        return ApiResponse.success(null);
    }

    /**
     * URL의 일정 ID와 로그인한 사용자의 ID가 모두 일치하는 일정을 삭제한다.
     * 삭제할 일정이 없거나 다른 사용자의 일정이면 404 Not Found를 반환한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSchedule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        scheduleService.deleteSchedule(loginUser.getId(), id);

        return ApiResponse.success(null);
    }
}
