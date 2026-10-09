package com.myhome.community;

import java.time.LocalDateTime;

/** 게시글 상세 정보다. 본문과 작성자 정보를 포함한다. */
public record CommunityDetailResponse(
        Long id,                  // 게시글 ID
        Long userId,              // 작성자 ID: 화면의 수정·삭제 버튼 표시 판단용
        String nickname,          // 작성자 닉네임
        String title,             // 게시글 제목
        String content,           // 게시글 본문
        Long viewCount,           // 조회수
        boolean isHidden,        // 숨김 여부: true이면 숨김
        LocalDateTime hiddenDt,   // 숨김 처리 일시, 미처리이면 null
        Long hiddenBy,            // 처리한 관리자 ID, 처음에는 NULL
        LocalDateTime createdDt,  // 작성 일시: DB의 UTC 기준
        LocalDateTime updatedDt   // 마지막 수정 일시: DB의 UTC 기준
) {
}