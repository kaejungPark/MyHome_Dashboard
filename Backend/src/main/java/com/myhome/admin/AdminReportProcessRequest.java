package com.myhome.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 관리자의 신고 처리 요청이다.
 * RESOLVED는 신고 수용 및 게시글 숨김, REJECTED는 신고 기각을 의미한다.
 * 처리 메모는 선택 입력이며 최대 1,000자까지 허용한다.
 * 처리자 ID와 처리일은 요청으로 받지 않고 서버에서 설정한다.
 */
public record AdminReportProcessRequest (
        @NotBlank(message = "신고 상태를 선택해주세요.")
        @Pattern(
                regexp = "RESOLVED|REJECTED"
        )
        String status,

        @Size(max = 1000, message = "처리 메모는 1,000자까지 입력할 수 있습니다.")
        String processNote
) {
}
