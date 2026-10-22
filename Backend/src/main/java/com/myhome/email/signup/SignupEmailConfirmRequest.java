package com.myhome.email.signup;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * 이메일에 발급한 인증번호가 일치하는지 확인한다.
 */
public record SignupEmailConfirmRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        @Size(max = 254, message = "이메일은 254자까지 입력할 수 있습니다.")
        String email,

        @NotNull(message = "인증 요청 정보를 확인해 주세요.")
        UUID requestId,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "인증번호는 숫자 6자리로 입력해 주세요."
        )
        String code
) {
    // 인증번호가 로그에 출력되지 않도록 기본 출력을 대체한다.
    @Override
    public String toString() {
        return "SignupEmailConfirmRequest[redacted]";
    }
}