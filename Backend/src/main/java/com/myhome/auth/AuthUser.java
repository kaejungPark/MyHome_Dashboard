package com.myhome.auth;

/**
 * 로그인 검증에 사용하는 사용자 정보다.
 * 비밀번호 해시를 포함하므로 API 응답이나 로그에 노출하지 않는다.
 */
public record AuthUser(
        Long id,             // 사용자 ID
        String email,        // 로그인 이메일
        String passwordHash, // 저장된 비밀번호 해시
        String nickname,     // 화면 표시 이름
        String role,         // 권한: USER, ADMIN
        String status        // 계정 상태: ACTIVE, SUSPENDED, WITHDRAWN
) {
}