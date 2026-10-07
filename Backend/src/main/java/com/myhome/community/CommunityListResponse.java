package com.myhome.community;

import java.time.LocalDateTime;

/** 게시글 목록의 한 항목이다. 본문은 포함하지 않는다. */
public record CommunityListResponse (
        Long id,                  // 게시글 ID
        String nickname,          // 작성자 닉네임
        String title,             // 게시글 제목
        Long viewCount,           // 조회수
        LocalDateTime createdDt   // 작성 일시: DB의 UTC 기준

){
}
