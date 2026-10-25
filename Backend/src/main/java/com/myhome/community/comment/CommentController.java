package com.myhome.community.comment;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글에 속한 댓글·대댓글 API를 제공한다.
 *
 * 조회는 비로그인 사용자도 가능하도록 SecurityConfig에서 허용한다.
 * 등록·수정·삭제는 로그인해야 하며 변경 요청에는 CSRF 토큰이 필요하다.
 *
 * 사용자 ID는 요청 본문에서 받지 않고 로그인 세션에서 가져온다.
 * 게시글 공개 여부·작성자 권한·부모 관계 검증은 서비스에서 처리한다.
 */
@RestController
@RequestMapping("/api/community/{communityId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * GET /api/community/{communityId}/comments
     *
     * 게시글의 일반 댓글을 작성순으로 페이징 조회한다.
     * 기본값은 1페이지·10개이며 대댓글은 포함하지 않는다.
     *
     * 삭제된 댓글은 원문 대신 삭제 안내 문구를 반환한다.
     * 댓글 조회로 게시글 조회수를 증가시키지는 않는다.
     */
    @GetMapping
    public ApiResponse<PageResponse<CommentResponse>> getComments(
            @PathVariable("communityId") Long communityId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                commentService.getComments(
                        communityId,
                        page,
                        size
                )
        );
    }

    /**
     * GET /api/community/{communityId}/comments/{parentId}/replies
     *
     * 특정 일반 댓글의 대댓글을 작성순으로 페이징 조회한다.
     * 일반 댓글 목록과 별도의 페이지 번호·크기를 사용한다.
     *
     * 부모 댓글이 삭제됐어도 이미 등록된 대댓글은 조회할 수 있다.
     * 부모가 해당 게시글의 일반 댓글인지는 서비스에서 확인한다.
     */
    @GetMapping("/{parentId}/replies")
    public ApiResponse<PageResponse<CommentResponse>> getReplies(
            @PathVariable("communityId") Long communityId,
            @PathVariable("parentId") Long parentId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                commentService.getReplies(
                        communityId,
                        parentId,
                        page,
                        size
                )
        );
    }

    /**
     * POST /api/community/{communityId}/comments
     *
     * 로그인한 사용자의 댓글 또는 대댓글을 등록한다.
     * 요청의 parentId가 null이면 일반 댓글, 값이 있으면 대댓글이다.
     *
     * @Valid로 본문 필수값·길이와 부모 ID의 양수 여부를 검사한다.
     * 부모의 존재·소속 게시글·삭제 상태·깊이는 서비스에서 검사한다.
     *
     * 정상 등록 시 201을 반환한다.
     * 등록한 ID는 반환하지 않으므로 화면에서는 목록을 다시 조회한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createComment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("communityId") Long communityId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        // 작성자 ID는 서버에서 인증한 로그인 정보만 사용한다.
        commentService.createComment(
                loginUser.getId(),
                communityId,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(null));
    }

    /**
     * PUT /api/community/{communityId}/comments/{commentId}
     *
     * 본인이 작성한 댓글 또는 대댓글의 본문을 수정한다.
     * 소속 게시글·작성자·부모 댓글은 수정하지 않는다.
     *
     * @Valid로 본문을 검사하고,
     * 서비스에서 작성자와 삭제 상태를 확인한다.
     */
    @PutMapping("/{commentId}")
    public ApiResponse<Void> updateComment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("communityId") Long communityId,
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        commentService.updateComment(
                loginUser.getId(),
                communityId,
                commentId,
                request
        );

        return ApiResponse.success(null);
    }

    /**
     * DELETE /api/community/{communityId}/comments/{commentId}
     *
     * 본인이 작성한 댓글 또는 대댓글을 논리 삭제한다.
     * 요청 본문 없이 URL의 ID와 로그인 사용자 정보로 처리한다.
     *
     * 부모 댓글을 삭제해도 기존 대댓글은 유지한다.
     * 실제 행 삭제는 게시글 자체를 삭제하는 서비스에서만 수행한다.
     */
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("communityId") Long communityId,
            @PathVariable("commentId") Long commentId
    ) {
        commentService.deleteComment(
                loginUser.getId(),
                communityId,
                commentId
        );

        return ApiResponse.success(null);
    }
}