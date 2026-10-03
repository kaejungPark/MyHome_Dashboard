package com.myhome.auth;

import jakarta.validation.constraints.*;

/**
 * 회원가입에 필요한 사용자 입력값을 받는다.
 */
public record SignupRequest(
        // 로그인 이메일: DB의 NVARCHAR(254)에 맞춰 길이를 제한한다.
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        @Size(max = 254, message = "이메일은 254자까지 입력할 수 있습니다.")
        String email,

        // 이름
        @Size(max = 100)
        String name,

        // 화면과 커뮤니티에서 표시할 이름이다.
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 100, message = "닉네임은 100자까지 입력할 수 있습니다.")
        String nickname,

        // 원문 비밀번호는 서비스에서 해시 처리한 뒤 저장한다.
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 12, message = "비밀번호는 12자 이상 입력해 주세요.")
        String password,

        // 선택 입력이므로 int 대신 null을 표현할 수 있는 Integer를 사용한다.
        // 입력한 경우에만 0~120세 범위를 검사한다.
        @Min(value = 0, message = "나이는 0 이상으로 입력해 주세요.")
        @Max(value = 120, message = "나이는 120 이하로 입력해 주세요.")
        Integer age,

        // 성별은 선택 입력이며, 미선택 시 null로 전달한다.
        @Pattern(
                regexp = "MALE|FEMALE",
                message = "올바른 성별을 선택해 주세요."
        )
        String gender
) {
}