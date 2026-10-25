package com.myhome.community.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 댓글 또는 대댓글 등록 요청이다.
 *
 * 게시글 ID는 URL에서 받고,
 * 작성자 ID는 로그인 세션에서 가져온다.
 *
 * 요청 본문에는 부모 댓글 ID와 내용만 받는다.
 */
public record CommentCreateRequest(

        /*
         * 일반 댓글이면 null이다.
         * 대댓글이면 답글을 달 일반 댓글의 ID다.
         *
         * @Positive는 null을 허용하고 값이 있을 때만 양수인지 검사한다.
         * 부모의 존재 여부·소속 게시글·삭제 여부·댓글 깊이는 서비스에서 검사한다.
         */
        @Positive(message = "부모 댓글 ID는 1 이상이어야 합니다.")
        Long parentId,

        /*
         * 댓글 본문이다.
         * DB의 CONTENT NVARCHAR(2000)에 맞춰 최대 2,000자로 제한한다.
         * 공백만 입력한 내용은 허용하지 않는다.
         */
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = 2000, message = "댓글은 2,000자까지 입력할 수 있습니다.")
        String content
) {
}