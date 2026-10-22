package com.myhome.email.signup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 전 인증번호를 받을 이메일을 전달한다.
 */
public record SignupEmailSendRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        @Size(max = 254, message = "이메일은 254자까지 입력할 수 있습니다.")
        String email
) {
}