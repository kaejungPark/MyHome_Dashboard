package com.myhome.auth;

import jakarta.validation.constraints.*;

/**
 * 회원 정보 수정 요청이다.
 * 이메일, 권한, 계정 상태는 수정 대상으로 받지 않는다.
 */
public record ProfileUpdateRequest(
        // 선택 입력 이름. 입력한 경우 최대 100자까지 허용한다.
        @Size(max = 100, message = "이름은 100자까지 입력할 수 있습니다.")
        String name,

        // 화면에 표시할 닉네임. 빈 값이나 공백만 입력하는 것은 허용하지 않는다.
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 100, message = "닉네임은 100자까지 입력할 수 있습니다.")
        String nickname,

        // 선택 입력 나이. 입력하지 않으면 null, 입력하면 0~120만 허용한다.
        @Min(value = 0, message = "나이는 0 이상으로 입력해 주세요.")
        @Max(value = 120, message = "나이는 120 이하로 입력해 주세요.")
        Integer age,

        // 선택 입력 성별. 미선택은 null이며 빈 문자열은 허용하지 않는다.
        @Pattern(
                regexp = "MALE|FEMALE",
                message = "올바른 성별을 선택해 주세요."
        )
        String gender,

        // 동의와 미동의 모두 허용하지만 요청에서 값을 빠뜨리면 거절한다.
        @NotNull(message = "이메일 알림 수신 여부를 선택해 주세요.")
        Boolean emailNotificationEnabled
) {
}