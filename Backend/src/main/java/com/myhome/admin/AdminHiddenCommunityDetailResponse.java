package com.myhome.admin;

import java.time.LocalDateTime;

/**
 * 관리자가 숨김 해제 여부를 검토할 게시글 상세 정보다.
 */
public record AdminHiddenCommunityDetailResponse(
        Long id,                    // 게시글 ID
        String title,               // 게시글 제목
        String content,             // 게시글 본문
        String authorNickname,      // 작성자 닉네임
        LocalDateTime createdDt,    // 게시글 작성 시각(UTC)
        boolean isHidden,           // 현재 숨김 여부
        Long hiddenBy,              // 숨김 처리한 관리자 ID
        LocalDateTime hiddenDt      // 숨김 처리 시각(UTC)
) {
}