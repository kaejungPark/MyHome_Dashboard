package com.myhome.auth;

/**
 * 화면에 필요한 로그인 사용자 정보만 반환한다.
 * 비밀번호와 해시는 포함하지 않는다.
 */
public record LoginResponse(
        Long id,
        String email,
        String nickname
) {
}