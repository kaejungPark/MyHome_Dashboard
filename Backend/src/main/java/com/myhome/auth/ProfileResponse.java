package com.myhome.auth;

/**
 * 회원 정보 수정 화면에 표시할 본인의 회원 정보다.
 * 비밀번호와 권한 정보는 포함하지 않는다.
 */
public record ProfileResponse(
        String email,           // 로그인 계정
        String name,            // 이름 — 미입력 시 null
        String nickname,        // 화면에 표시할 닉네임
        Integer age,            // 나이 — 미입력 시 null
        String gender,          // 성별 — 미입력 시 null
        boolean emailVerified , // 이메일 인증 완료 여부
        boolean emailNotificationEnabled    // 이메일 알림 수신 동의 여부
) {
}