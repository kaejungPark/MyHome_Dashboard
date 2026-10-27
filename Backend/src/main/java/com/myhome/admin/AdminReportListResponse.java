package com.myhome.admin;

import java.time.LocalDateTime;

/**
 * 관리자 신고 목록의 한 항목을 반환한다.
 * 신고 대상 게시글, 신고자, 신고 사유와 처리 상태를 제공한다.
 * 페이징 정보는 공통 PageResponse에서 관리한다.
 */
public record AdminReportListResponse(
        Long id,                    // 신고 ID. 신고 상세 조회와 처리에 사용
        Long communityId,           // 신고 대상 게시글 ID
        String title,               // 신고 대상 게시글 제목
        String reporterNickname,    // 신고한 사용자의 닉네임
        String reason,              // 신고 사유 코드
        String status,              // PENDING: 대기, RESOLVED: 수용, REJECTED: 기각
        LocalDateTime createdDt     // 신고 접수 시각(UTC)
) {
}
