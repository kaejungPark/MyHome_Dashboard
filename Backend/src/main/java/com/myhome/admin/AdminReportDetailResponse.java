package com.myhome.admin;

import java.time.LocalDateTime;

/**
 * 관리자가 신고 내용을 검토하는 데 필요한 상세 정보를 반환한다.
 * 신고 설명, 대상 게시글 본문과 숨김 여부, 처리 이력을 포함한다.
 * 처리 전에는 처리자 ID, 처리일, 처리 메모가 null일 수 있다.
 */
public record AdminReportDetailResponse (
        Long id,
        Long communityId,
        String title,
        String reporterNickname,
        String reason,
        String status,
        LocalDateTime createdDt,
        String description,
        String content,
        String authorNickname,
        boolean isHidden,
        Long processedBy,
        LocalDateTime processedDt,
        String processNote

) {
}
