package com.myhome.community.comment;

import java.time.LocalDateTime;

/**
 * 화면에 반환하는 댓글 또는 대댓글 정보다.
 *
 * parentId가 null이면 일반 댓글이며,
 * 값이 있으면 해당 부모 댓글에 속한 대댓글이다.
 *
 * 삭제된 댓글은 대댓글 연결을 유지하기 위해 목록에 남길 수 있지만,
 * 원문은 반환하지 않고 "삭제된 댓글입니다."로 대체한다.
 * 작성자 정보도 조회·변환 과정에서 null로 처리한다.
 */
public record CommentResponse(

        // 댓글 ID. 수정·삭제 요청의 대상 식별자로 사용한다.
        Long id,

        // 댓글이 속한 게시글 ID
        Long communityId,

        // 작성자 ID 삭제된 댓글은 null로 반환한다.
        Long userId,

        // 작성자의 현재 닉네임 삭제된 댓글은 null로 반환한다.
        String nickname,

        // 일반 댓글은 null, 대댓글은 부모 댓글 ID다.
        Long parentId,

        // 정상 댓글은 본문, 삭제된 댓글은 삭제 안내 문구다.
        String content,

        // 삭제된 댓글의 수정·삭제 버튼을 숨기는 데 사용한다.
        boolean isDeleted,

        // 댓글 작성 시각 DB의 UTC 값을 사용한다.
        LocalDateTime createdDt,

        // 마지막 수정 시각 DB의 UTC 값을 사용한다.
        LocalDateTime updatedDt
) {
}