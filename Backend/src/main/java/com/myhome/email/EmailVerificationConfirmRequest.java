package com.myhome.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * 이메일 인증번호 확인 요청이다.
 * 사용자 ID와 이메일은 요청으로 받지 않고 서버에서 확인한다.
 */
public record EmailVerificationConfirmRequest(
        @NotNull(message = "인증 요청 정보가 필요합니다.")
        UUID requestId, // 발송 응답으로 받은 요청 식별자

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "인증번호는 숫자 6자리로 입력해 주세요."
        )
        String code // 앞자리 0을 유지하기 위해 문자열로 받는다.
) {
}