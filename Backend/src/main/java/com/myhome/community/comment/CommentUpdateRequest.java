package com.myhome.community.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기존 댓글 또는 대댓글의 내용 수정 요청이다.
 *
 * 댓글 ID와 게시글 ID는 URL에서 받고,
 * 수정 권한 확인에 사용할 사용자 ID는 로그인 세션에서 가져온다.
 *
 * 작성자·소속 게시글·부모 댓글은 수정할 수 없다.
 */
public record CommentUpdateRequest(

        // 수정할 본문이다. 작성자 확인과 삭제 상태 검사는 서비스에서 수행한다.
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = 2000, message = "댓글은 2,000자까지 입력할 수 있습니다.")
        String content
) {
}