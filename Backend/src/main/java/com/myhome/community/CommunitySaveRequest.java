package com.myhome.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 등록·수정에서 공통으로 사용하는 요청 데이터다.
 * 제목과 본문의 필수 여부 및 최대 길이를 검증한다.
 * 작성자 ID는 요청으로 받지 않고 로그인 정보에서 가져온다.
 */
public record CommunitySaveRequest (
        // 제목
        @NotBlank(message = "제목 입력은 필수입니다.")
        @Size(max = 200, message = "제목은 200자까지 입력할 수 있습니다.")
        String title,

        // 내용
        @NotBlank(message = "내용 입력은 필수입니다.")
        @Size(max = 10000, message = "내용은 10000자까지 입력할 수 있습니다.")
        String content
){

}
