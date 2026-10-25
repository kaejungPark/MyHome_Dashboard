package com.myhome.community.comment;

/**
 * 서비스의 권한·부모 관계 검증에 사용하는 서버 내부 댓글 정보다.
 *
 * 화면용 CommentResponse와 달리 삭제된 댓글도 실제 작성자 ID를 유지한다.
 * 검증에 필요하지 않은 본문과 작성자 닉네임은 조회하지 않는다.
 *
 * 이 record는 API 응답으로 직접 반환하지 않는다.
 */
public record Comment(

        // 수정·삭제하거나 부모로 선택한 댓글의 ID다.
        Long id,

        // URL로 받은 게시글 ID와 일치하는지 확인한다.
        Long communityId,

        // 로그인한 사용자가 작성자인지 확인한다.
        Long userId,

        /*
         * null이면 일반 댓글이다.
         * 부모로 선택한 댓글의 parentId가 null이 아니면 이미 대댓글이므로
         * 추가 대댓글 등록을 거절한다.
         */
        Long parentId,

        // 삭제된 댓글의 수정·재삭제·새 대댓글 등록을 제한할 때 사용한다.
        boolean isDeleted
) {
}