package com.myhome.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청이다.
 * 새 비밀번호 확인은 프론트에서 검사하며 서버에는 전달하지 않는다.
 */
public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "현재 비밀번호가 너무 깁니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(
                min = 12,
                max = 72,
                message = "새 비밀번호는 12~72자 사이로 입력해 주세요."
        )
        String newPassword
) {
}