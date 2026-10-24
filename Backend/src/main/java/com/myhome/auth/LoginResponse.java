package com.myhome.auth;

/**
 * 화면에 필요한 로그인 사용자 정보만 반환한다.
 *
 * 비밀번호와 해시는 포함하지 않는다.
 * 임시 로그인 여부를 함께 반환하여 프론트의 화면 이동에 사용한다.
 */
public record LoginResponse(
        Long id,         // 로그인한 회원 ID
        String email,    // 로그인 이메일
        String nickname, // 화면에 표시할 닉네임

        // USER: 일반 회원, ADMIN: 관리자, PASSWORD_RESET: 임시 로그인
        String role,

        // true이면 일반 기능 이용 전에 새 비밀번호로 변경해야 한다.
        boolean passwordChangeRequired
) {
}