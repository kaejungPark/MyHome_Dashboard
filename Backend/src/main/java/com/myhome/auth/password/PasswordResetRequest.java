package com.myhome.auth.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 찾기 화면에서 전달하는 임시 비밀번호 발송 요청이다.
 *
 * 사용자가 입력한 이메일을 받아 해당 회원의 발급 가능 여부를 확인한다.
 * 장보기 알림 수신 동의와는 별개인 계정 복구 요청이다.
 *
 * 실제 수신 주소는 서비스에서 조회한 DB의 이메일을 사용한다.
 */
public record PasswordResetRequest(
        // 임시 비밀번호를 요청할 회원의 로그인 이메일이다.
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 확인해 주세요.")
        @Size(max = 254, message = "이메일이 너무 깁니다.")
        String email
) {
}