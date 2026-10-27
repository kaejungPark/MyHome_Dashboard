package com.myhome.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일과 비밀번호를 표현하는 요청 형식이다.
 * 현재 로그인은 Spring Security의 폼 로그인으로 처리하므로 이 record를 사용하지 않는다.
 * 따라서 여기에 선언한 검증 조건도 현재 로그인 요청에는 적용되지 않는다.
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