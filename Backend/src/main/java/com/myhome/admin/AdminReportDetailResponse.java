package com.myhome.admin;

import java.time.LocalDateTime;

/**
 * 관리자가 신고 내용을 검토하는 데 필요한 상세 정보를 반환한다.
 * 신고 설명, 대상 게시글 본문과 숨김 여부, 처리 이력을 포함한다.
 * 처리 전에는 처리자 ID, 처리일, 처리 메모가 null일 수 있다.
 */
public record AdminReportDetailResponse(
        Long id,                    // 신고 ID
        Long communityId,           // 신고 대상 게시글 ID
        String title,               // 신고 대상 게시글 제목
        String reporterNickname,    // 신고한 사용자의 닉네임
        String reason,              // 신고 사유 코드
        String status,              // 현재 신고 처리 상태
        LocalDateTime createdDt,    // 신고 접수 시각(UTC)
        String description,         // 신고자가 입력한 상세 설명. 없으면 null
        String content,             // 신고 대상 게시글 본문
        String authorNickname,      // 게시글 작성자의 닉네임
        boolean isHidden,           // 대상 게시글의 현재 숨김 여부
        Long processedBy,           // 처리한 관리자 ID. 처리 전에는 null
        LocalDateTime processedDt,  // 신고 처리 시각(UTC). 처리 전에는 null
        String processNote          // 관리자의 처리 의견. 입력하지 않으면 null
) {
}
