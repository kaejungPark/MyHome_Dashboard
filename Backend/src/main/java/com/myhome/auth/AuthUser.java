package com.myhome.auth;

/**
 * 로그인 검증과 회원 상태 확인에 사용하는 서버 내부 회원 정보다.
 *
 * 비밀번호 해시를 포함하므로 API 응답이나 로그에 직접 노출하지 않는다.
 * AuthMapper의 조회 컬럼과 이 record의 구성 요소를 함께 맞춰야 한다.
 */
public record AuthUser(
        Long id,             // 사용자 ID
        String email,        // 로그인 이메일
        String passwordHash, // 저장된 일반 비밀번호의 BCrypt 해시
        String nickname,     // 화면에 표시할 닉네임
        String role,         // 권한: USER, ADMIN
        String status,       // 계정 상태: ACTIVE, SUSPENDED, WITHDRAWN

        /*
         * 비밀번호 변경 시 증가하는 인증 버전이다.
         * 로그인 당시의 버전과 현재 DB 버전이 다르면 이전 세션을 거절한다.
         */
        int authVersion
) {
}