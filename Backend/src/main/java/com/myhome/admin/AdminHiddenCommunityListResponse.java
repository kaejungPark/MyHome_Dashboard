package com.myhome.admin;

import java.time.LocalDateTime;

/**
 * 관리자 숨김 게시글 목록의 한 항목이다.
 */
public record AdminHiddenCommunityListResponse(
        Long id,                    // 게시글 ID
        String title,               // 게시글 제목
        String authorNickname,      // 작성자 닉네임
        LocalDateTime createdDt,    // 게시글 작성 시각(UTC)
        Long hiddenBy,              // 숨김 처리한 관리자 ID
        LocalDateTime hiddenDt      // 숨김 처리 시각(UTC)
) {
}