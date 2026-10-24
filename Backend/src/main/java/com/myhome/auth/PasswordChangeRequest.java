package com.myhome.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 후 비밀번호를 변경하는 요청이다.
 *
 * 일반 로그인에서는 현재 일반 비밀번호를,
 * 임시 로그인에서는 이메일로 받은 임시 비밀번호를 전달한다.
 *
 * 새 비밀번호 확인은 프론트에서 검사하며 서버에는 전달하지 않는다.
 * 서버는 새 비밀번호의 길이와 기존 비밀번호 재사용 여부를 별도로 검사한다.
 */
public record PasswordChangeRequest(
        // 로그인 종류에 따라 현재 일반 비밀번호 또는 임시 비밀번호다.
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "현재 비밀번호가 너무 깁니다.")
        String currentPassword,

        // 실제로 저장할 새 비밀번호 원문이다. 서비스에서 해시로 변환한다.
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(
                min = 12,
                max = 72,
                message = "새 비밀번호는 12~72자 사이로 입력해 주세요."
        )
        String newPassword
) {
}