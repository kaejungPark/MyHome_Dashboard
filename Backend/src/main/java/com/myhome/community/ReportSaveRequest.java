package com.myhome.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 게시글 신고 등록 요청이다.
 * 신고 사유는 지정된 코드만 허용하며, 상세 설명은 선택 입력이다.
 * 신고자 ID는 요청으로 받지 않고 로그인 정보에서 가져온다.
 */
public record ReportSaveRequest(
        @NotBlank(message = "신고 사유를 선택해주세요.")
        @Pattern(
                regexp = "SPAM|ABUSE|INAPPROPRIATE|OTHER",
                message = "올바른 신고 사유를 선택해 주세요."
        )
        @Size(max = 30)
        String reason,

        @Size(max = 1000, message = "신고 상세 설명은 1000자까지 입력할 수 있습니다.")
        String description
){
}
