package com.myhome.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminHiddenCommunityMapper {

    /**
     * 작성자의 계정 상태와 관계없이 숨김 게시글 수를 조회한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.COMMUNITY
        WHERE IS_HIDDEN = 1
        """)
    long countHidden();

    /**
     * 숨김 처리 시각이 최신인 게시글부터 페이지 조회한다.
     * 같은 시각이면 게시글 ID 내림차순으로 정렬한다.
     */
    @Select("""
        SELECT
            c.ID AS id,
            c.TITLE AS title,
            u.NICKNAME AS authorNickname,
            c.CREATED_DT AS createdDt,
            c.HIDDEN_BY AS hiddenBy,
            c.HIDDEN_DT AS hiddenDt
        FROM dbo.COMMUNITY c
        LEFT JOIN dbo.[USER] u ON u.ID = c.USER_ID
        WHERE c.IS_HIDDEN = 1
        ORDER BY c.HIDDEN_DT DESC, c.ID DESC
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
        """)
    List<AdminHiddenCommunityListResponse> findHiddenPage(
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 게시글의 내용과 현재 숨김 상태를 조회한다.
     * 서비스에서 미존재와 이미 공개된 상태를 구분하도록
     * IS_HIDDEN 조건은 적용하지 않는다.
     */
    @Select("""
        SELECT
            c.ID AS id,
            c.TITLE AS title,
            c.CONTENT AS content,
            u.NICKNAME AS authorNickname,
            c.CREATED_DT AS createdDt,
            c.IS_HIDDEN AS isHidden,
            c.HIDDEN_BY AS hiddenBy,
            c.HIDDEN_DT AS hiddenDt
        FROM dbo.COMMUNITY c
        LEFT JOIN dbo.[USER] u ON u.ID = c.USER_ID
        WHERE c.ID = #{id}
        """)
    AdminHiddenCommunityDetailResponse findById(
            @Param("id") Long id
    );

    /**
     * 숨김 해제 실패 시 게시글 삭제 여부를 확인한다.
     */
    @Select("""
        SELECT CAST(
            CASE WHEN EXISTS (
                SELECT 1
                FROM dbo.COMMUNITY
                WHERE ID = #{id}
            ) THEN 1 ELSE 0 END
            AS BIT
        )
        """)
    boolean existsById(@Param("id") Long id);

    /**
     * 현재 숨김 상태인 게시글만 공개로 변경한다.
     * 현재 숨김 처리자와 시각을 초기화하며 신고 처리 이력은 변경하지 않는다.
     * 이미 공개 상태이거나 게시글이 없으면 0을 반환한다.
     */
    @Update("""
        UPDATE dbo.COMMUNITY
        SET IS_HIDDEN = 0,
            HIDDEN_BY = NULL,
            HIDDEN_DT = NULL
        WHERE ID = #{id}
          AND IS_HIDDEN = 1
        """)
    int restore(@Param("id") Long id);
}