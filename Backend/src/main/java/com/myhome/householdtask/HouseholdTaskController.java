package com.myhome.householdtask;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/household-tasks")
public class HouseholdTaskController {
    private final HouseholdTaskService householdTaskService;

    public HouseholdTaskController(HouseholdTaskService householdTaskService) {
        this.householdTaskService = householdTaskService;
    }

    /**
     * 로그인한 사용자의 집안일·소모품 관리 항목을 조회한다.
     */
    @GetMapping
    public ApiResponse<List<HouseholdTaskResponse>> getTasks (@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(householdTaskService.getTasks(loginUser.getId()));
    }

    /**
     * 관리 항목의 입력값을 검증하고 등록 성공 시 201을 반환한다.
     * 소유자 ID는 요청 본문이 아닌 로그인 정보에서 가져온다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createTask (
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody HouseholdTaskSaveRequest request
    ) {
        householdTaskService.createTask(loginUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * 관리 항목 ID와 로그인 사용자 ID를 전달해 본인 항목을 수정한다.
     * 반복 주기, 다음 예정일과 활성 여부를 함께 변경할 수 있다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateTask (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody HouseholdTaskSaveRequest request
    ) {
        householdTaskService.updateTask(loginUser.getId(), id, request);
        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자의 관리 항목 삭제를 요청한다.
     * 소유 여부와 완료 이력에 따른 삭제 가능 여부는 서비스에서 검사한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask  (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        householdTaskService.deleteTask(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

    /**
     * 관리 항목의 완료일과 메모를 받아 완료 처리를 요청한다.
     * 서비스에서 완료 이력 저장과 다음 예정일 갱신을 함께 처리한다.
     * 완료 이력 등록에 성공하면 201을 반환한다.
     */
    @PostMapping("/{id}/completions")
    public ResponseEntity<ApiResponse<Void>> createComplet (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody HouseholdTaskCompleteRequest request
    ) {
        householdTaskService.createComplet(loginUser.getId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * 지정한 관리 항목의 완료 이력을 조회한다.
     * 경로의 id는 완료 이력 ID가 아닌 관리 항목 ID다.
     * 본인 소유 항목인지 서비스에서 확인한다.
     */
    @GetMapping("/{id}/completions")
    public ApiResponse<List<HouseholdTaskHistoryResponse>> getHistorys (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        return ApiResponse.success(householdTaskService.getHistorys(loginUser.getId(), id));
    }

}
