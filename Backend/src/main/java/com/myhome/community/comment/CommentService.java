package com.myhome.community.comment;

import com.myhome.common.response.PageResponse;
import com.myhome.common.util.PageUtils;
import com.myhome.common.validation.PageValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 댓글·대댓글의 조회와 등록·수정·삭제를 처리한다.
 *
 * 일반 댓글과 대댓글은 같은 테이블을 사용한다.
 * 대댓글은 일반 댓글 바로 아래의 한 단계까지만 허용한다.
 *
 * 변경 작업은 공개 게시글을 먼저 잠근 뒤 검증하고 처리한다.
 * 같은 게시글의 댓글 변경과 게시글 삭제가 충돌하지 않도록
 * 게시글 삭제 서비스에서도 동일한 잠금 순서를 사용해야 한다.
 */
@Service
public class CommentService {

    private final CommentMapper commentMapper;

    public CommentService(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    /**
     * 공개 게시글의 일반 댓글을 페이지 단위로 조회한다.
     *
     * 대댓글은 이 목록에 포함하지 않는다.
     * 삭제된 댓글은 삭제 안내 자리로 포함하며 원문은 매퍼에서 숨긴다.
     *
     * 게시글이 없거나 숨김 상태이면 404를 반환한다.
     * 댓글이 없거나 페이지 범위를 초과하면 빈 목록을 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getComments(
            Long communityId,
            int page,
            int size
    ) {
        requirePositiveId(communityId, "게시글 ID");
        PageValidator.validate(page, size);

        // 게시글이 없는 경우와 댓글만 없는 경우를 구분한다.
        requireVisibleCommunity(communityId);

        long offset = PageUtils.calculateOffset(page, size);
        long totalCount = commentMapper.countRoots(communityId);
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 불필요한 목록 쿼리를 줄이고 기존 페이징 처리 방식과 맞춘다.
        List<CommentResponse> items = offset >= totalCount
                ? List.of()
                : commentMapper.findRootPage(communityId, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 특정 일반 댓글의 대댓글을 페이지 단위로 조회한다.
     *
     * 부모 댓글은 같은 게시글에 속한 일반 댓글이어야 한다.
     * 부모가 삭제됐어도 이미 등록된 대댓글은 조회할 수 있다.
     *
     * 삭제된 부모에 새 대댓글을 등록하는 것과
     * 기존 대댓글을 조회하는 것은 서로 다른 정책이다.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getReplies(
            Long communityId,
            Long parentId,
            int page,
            int size
    ) {
        requirePositiveId(communityId, "게시글 ID");
        requirePositiveId(parentId, "부모 댓글 ID");
        PageValidator.validate(page, size);

        requireVisibleCommunity(communityId);

        // 다른 게시글의 댓글 ID를 넘기면 조회되지 않는다.
        Comment parent = requireComment(communityId, parentId);

        // 대댓글을 부모로 지정한 조회는 허용하지 않는다.
        if (parent.parentId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "일반 댓글의 대댓글만 조회할 수 있습니다."
            );
        }

        // 부모의 isDeleted는 검사하지 않는다.
        // 부모가 삭제돼도 기존 대댓글 목록은 유지한다.
        long offset = PageUtils.calculateOffset(page, size);
        long totalCount =
                commentMapper.countReplies(communityId, parentId);
        long totalPages =
                PageUtils.calculateTotalPages(totalCount, size);

        List<CommentResponse> items = offset >= totalCount
                ? List.of()
                : commentMapper.findReplyPage(
                communityId,
                parentId,
                offset,
                size
        );

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 로그인한 회원의 댓글 또는 대댓글을 등록한다.
     *
     * parentId가 null이면 일반 댓글을 등록한다.
     * 값이 있으면 같은 게시글의 삭제되지 않은 일반 댓글인지 확인한다.
     *
     * 사용자 ID는 컨트롤러가 로그인 세션에서 가져와 전달해야 한다.
     */
    @Transactional
    public void createComment(
            Long userId,
            Long communityId,
            CommentCreateRequest request
    ) {
        requireLoginUser(userId);
        requirePositiveId(communityId, "게시글 ID");

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "댓글 등록 내용을 확인해 주세요."
            );
        }

        String content = requireContent(request.content());
        Long parentId = request.parentId();

        if (parentId != null) {
            requirePositiveId(parentId, "부모 댓글 ID");
        }

        /*
         * 부모 조회보다 먼저 게시글을 잠근다.
         * 같은 게시글의 댓글 삭제와 대댓글 등록도 이 잠금을 사용하므로
         * 부모 검증 후 등록하기 전에 부모가 삭제되는 상황을 방지한다.
         */
        lockVisibleCommunity(communityId);

        if (parentId != null) {
            Comment parent = requireComment(communityId, parentId);

            // 대댓글 아래에 다시 대댓글을 등록하지 못하도록 제한한다.
            if (parent.parentId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "대댓글에는 답글을 등록할 수 없습니다."
                );
            }

            // 기존 대댓글 조회는 허용하지만 새 대댓글 등록은 차단한다.
            if (parent.isDeleted()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "삭제된 댓글에는 답글을 등록할 수 없습니다."
                );
            }
        }

        int insertedRows = commentMapper.insert(
                communityId,
                userId,
                parentId,
                content
        );

        requireOneChangedRow(insertedRows, "댓글 등록");
    }

    /**
     * 로그인한 회원이 작성한 댓글 또는 대댓글의 본문을 수정한다.
     *
     * 다른 회원의 댓글은 수정할 수 없다.
     * 삭제된 댓글은 수정할 수 없다.
     * 부모 댓글과 소속 게시글은 변경하지 않는다.
     */
    @Transactional
    public void updateComment(
            Long userId,
            Long communityId,
            Long commentId,
            CommentUpdateRequest request
    ) {
        requireLoginUser(userId);
        requirePositiveId(communityId, "게시글 ID");
        requirePositiveId(commentId, "댓글 ID");

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "댓글 수정 내용을 확인해 주세요."
            );
        }

        String content = requireContent(request.content());

        lockVisibleCommunity(communityId);

        // 작성자 여부를 먼저 검사한 후 삭제 상태를 확인한다.
        Comment comment = requireOwnedComment(
                userId,
                communityId,
                commentId
        );

        if (comment.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "삭제된 댓글은 수정할 수 없습니다."
            );
        }

        int updatedRows = commentMapper.update(
                communityId,
                commentId,
                userId,
                content
        );

        requireOneChangedRow(updatedRows, "댓글 수정");
    }

    /**
     * 본인이 작성한 댓글 또는 대댓글을 논리 삭제한다.
     *
     * 부모 댓글을 삭제해도 기존 대댓글은 함께 삭제하지 않는다.
     * 행과 부모 연결은 유지하고 일반 조회에서는 원문을 숨긴다.
     *
     * 이미 삭제된 댓글을 다시 삭제하면 409를 반환한다.
     */
    @Transactional
    public void deleteComment(
            Long userId,
            Long communityId,
            Long commentId
    ) {
        requireLoginUser(userId);
        requirePositiveId(communityId, "게시글 ID");
        requirePositiveId(commentId, "댓글 ID");

        lockVisibleCommunity(communityId);

        Comment comment = requireOwnedComment(
                userId,
                communityId,
                commentId
        );

        if (comment.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 삭제된 댓글입니다."
            );
        }

        int deletedRows = commentMapper.softDelete(
                communityId,
                commentId,
                userId
        );

        requireOneChangedRow(deletedRows, "댓글 삭제");
    }

    /**
     * 조회 대상이 공개 게시글인지 확인한다.
     * 조회수는 증가시키지 않는다.
     */
    private void requireVisibleCommunity(Long communityId) {
        if (!commentMapper.existsVisibleCommunity(communityId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }
    }

    /**
     * 변경 대상 공개 게시글을 잠근다.
     *
     * public 변경 메서드의 @Transactional 안에서 호출되므로
     * 이 메서드가 끝나도 잠금은 해당 트랜잭션 종료까지 유지된다.
     */
    private void lockVisibleCommunity(Long communityId) {
        if (commentMapper.lockVisibleCommunity(communityId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }
    }

    /**
     * 지정한 게시글에 속한 댓글을 조회한다.
     * 다른 게시글의 댓글이거나 존재하지 않으면 404를 반환한다.
     */
    private Comment requireComment(
            Long communityId,
            Long commentId
    ) {
        Comment comment =
                commentMapper.findById(communityId, commentId);

        if (comment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "댓글을 찾을 수 없습니다."
            );
        }

        return comment;
    }

    /**
     * 본인이 작성한 댓글인지 확인한다.
     *
     * 기존 게시글 수정 정책과 맞춰 다른 작성자의 댓글이면 404를 반환한다.
     * 관리자라는 이유만으로 일반 수정·삭제 API에서 타인의 댓글을 변경하지 않는다.
     */
    private Comment requireOwnedComment(
            Long userId,
            Long communityId,
            Long commentId
    ) {
        Comment comment = requireComment(communityId, commentId);

        if (!userId.equals(comment.userId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "댓글을 찾을 수 없습니다."
            );
        }

        return comment;
    }

    /**
     * 로그인 사용자 ID가 전달됐는지 확인한다.
     *
     * 실제 로그인·계정 상태·임시 로그인 제한은 Security에서 검사한다.
     * 이 검사는 사용자 ID가 잘못 전달되는 경우를 방어한다.
     */
    private void requireLoginUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }
    }

    /** URL이나 부모 지정값으로 사용할 ID가 양수인지 검사한다. */
    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    name + "는 1 이상이어야 합니다."
            );
        }
    }

    /**
     * 댓글 본문을 검사하고 원문을 반환한다.
     *
     * 컨트롤러의 @Valid와 별개로 서비스에서도 기본 조건을 확인한다.
     * 줄바꿈과 작성한 공백을 유지하기 위해 내용 자체는 trim하지 않는다.
     */
    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "댓글 내용을 입력해 주세요."
            );
        }

        if (content.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "댓글은 2,000자까지 입력할 수 있습니다."
            );
        }

        return content;
    }

    /**
     * 댓글 한 건의 등록·수정·삭제 결과를 확인한다.
     *
     * 0건이면 변경 조건을 만족하지 못한 것으로 안내한다.
     * 1건을 초과하는 예상하지 못한 결과는 서버 오류로 처리한다.
     * 예외가 전파되면 호출한 변경 메서드의 트랜잭션이 롤백된다.
     */
    private void requireOneChangedRow(int changedRows, String action) {
        if (changedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    action + " 조건이 변경되었습니다. 다시 조회해 주세요."
            );
        }

        if (changedRows != 1) {
            throw new IllegalStateException(
                    action + " 중 예상하지 못한 처리 건수가 발생했습니다."
            );
        }
    }
}