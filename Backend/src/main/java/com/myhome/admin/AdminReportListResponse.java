package com.myhome.admin;

import java.time.LocalDateTime;

/**
 * 관리자 신고 목록의 한 항목을 반환한다.
 * 신고 대상 게시글, 신고자, 신고 사유와 처리 상태를 제공한다.
 * 페이징 정보는 공통 PageResponse에서 관리한다.
 */
public record AdminReportListResponse (
        Long id,
        Long communityId,
        String title,
        String reporterNickname,
        String reason,
        String status,
        LocalDateTime createdDt

) {
}
