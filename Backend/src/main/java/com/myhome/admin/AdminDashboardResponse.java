package com.myhome.admin;

public record AdminDashboardResponse (
        long totalUserCount,         // 탈퇴 상태를 제외한 전체 회원 수
        long todaySignupCount,       // 오늘 가입한 회원 수
        long pendingReportCount,     // 처리 대기 신고 수(신고 상태가 PENDING)
        long hiddenCommunityCount    // 숨김 게시글 수(게시글의 IS_HIDDEN = 1)
){
}
