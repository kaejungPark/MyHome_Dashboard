package com.myhome.admin;

/**
 * 관리자 대시보드 상단에 표시할 네 가지 통계다.
 * 각 필드에는 조건에 맞는 전체 건수가 들어간다.
 */
public record AdminDashboardResponse (
        long totalUserCount,         // 탈퇴 상태를 제외한 전체 회원 수
        long todaySignupCount,       // 오늘 가입한 회원 수
        long pendingReportCount,     // 처리 대기 신고 수(신고 상태가 PENDING)
        long hiddenCommunityCount    // 숨김 게시글 수(게시글의 IS_HIDDEN = 1)
){
}
