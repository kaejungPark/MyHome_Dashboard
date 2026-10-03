package com.myhome.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인에 필요한 이메일과 비밀번호를 받는다.
 * 비밀번호는 저장된 해시와 비교하며 로그에 남기지 않는다.
 */
public record LoginRequest(

        // 회원가입 시 등록한 로그인 이메일
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        String email,

        // 사용자가 입력한 원문 비밀번호
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}