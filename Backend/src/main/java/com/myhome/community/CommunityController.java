package com.myhome.community;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 커뮤니티 화면의 게시글 조회·등록·수정·삭제와 신고 요청을 받는다.
 * 요청값을 서비스에 전달하고 처리 결과를 공통 응답 형식으로 반환한다.
 * 작성자와 신고자는 화면에서 전달한 값이 아니라 로그인 정보로 확인한다.
 */
@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * 검색 조건에 맞는 게시글 목록을 페이지 단위로 조회한다.
     * 비로그인 사용자도 조회할 수 있으며, 숨김 게시글은 제외한다.
     *
     * page와 size는 조회할 페이지 번호와 페이지당 게시글 수다.
     * searchType은 제목(TITLE) 또는 닉네임(NICKNAME)이며,
     * keyword가 비어 있으면 검색어 조건 없이 조회한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<CommunityListResponse>> getCommunities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "TITLE") String searchType,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ApiResponse.success(communityService.getCommunities(page, size, searchType, keyword));
    }

    /**
     * 게시글 ID로 상세 정보를 조회한다. 비로그인 사용자도 조회할 수 있다.
     * */
    @GetMapping("/{id}")
    public ApiResponse<CommunityDetailResponse> getCommunity(@PathVariable ("id") Long id)
    {
        return ApiResponse.success(communityService.getCommunity(id));
    }

    /**
     * GET /api/community/{id}/edit: 수정 폼에 필요한 게시글 정보를 조회한다.
     * 게시글 ID와 로그인 사용자 ID를 서비스에 전달하여 작성자 여부를 확인한다.
     * 본인 글만 조회할 수 있으며, 조회수는 증가시키지 않는다.
     */
    @GetMapping("/{id}/edit")
    public ApiResponse<CommunityDetailResponse> getEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable ("id") Long id) {
        return ApiResponse.success(communityService.getEdit(loginUser.getId(), id));
    }

    /**
     * 로그인한 사용자의 게시글을 등록하고 성공 시 201을 반환한다.
     * */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createCommunity (
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CommunitySaveRequest request
    ) {
        communityService.createCommunity(loginUser.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * 게시글 ID와 로그인 사용자 정보를 전달하여 본인 글을 수정한다.
     * */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateCommunity (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody CommunitySaveRequest request
    ) {
        communityService.updateCommunity(loginUser.getId(), id, request);

        return ApiResponse.success(null);
    }

    /**
     * 게시글 ID와 로그인 사용자 정보를 전달하여 본인 글을 삭제한다.
     * */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCommunity(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        communityService.deleteCommunity(loginUser.getId(), id);

        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자가 다른 사람의 게시글을 신고한다.
     * 신고자 ID는 로그인 정보에서 가져오고, 신고 내용은 요청 본문으로 받는다.
     * 신고 접수에 성공하면 201을 반환한다.
     */
    @PostMapping("/{id}/reports")
    public ResponseEntity<ApiResponse<Void>> createReport (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody ReportSaveRequest request
    ) {
        communityService.createReport(loginUser.getId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

}
