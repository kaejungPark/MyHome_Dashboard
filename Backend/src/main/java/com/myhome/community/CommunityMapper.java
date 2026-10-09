package com.myhome.community;

import jakarta.validation.Valid;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommunityMapper {

    /**
     * 검색 조건에 맞는 전체 게시글 수를 조회한다.
     * 검색어가 없으면 전체 개수를 반환한다.
     */
    @Select("""
    <script>
    SELECT COUNT_BIG(*)
    FROM dbo.COMMUNITY c
    JOIN dbo.[USER] u ON u.ID = c.USER_ID
    <where>
        /* 숨김 처리되지 않은 게시글만 조회 (기본 조건) */
        c.IS_HIDDEN = 0
        <if test="keyword != null and keyword != ''">
            <choose>
                <when test="searchType == 'NICKNAME'">
                    AND CHARINDEX(#{keyword,jdbcType=NVARCHAR}, u.NICKNAME) > 0
                </when>
                <otherwise>
                    AND CHARINDEX(#{keyword,jdbcType=NVARCHAR}, c.TITLE) > 0
                </otherwise>
            </choose>
        </if>
    </where>
    </script>
    """)
    long countAll(
            @Param("searchType") String searchType,
            @Param("keyword") String keyword
    );

    /**
     * 검색 조건에 맞는 게시글을 최신순으로 페이지 조회한다. (숨김 글 제외)
     * 본문은 제외하며, 목록 조회에서는 조회수를 증가시키지 않는다.
     */
    @Select("""
    <script>
    SELECT
        c.ID AS id,
        u.NICKNAME AS nickname,
        c.TITLE AS title,
        c.VIEW_COUNT AS viewCount,
        c.CREATED_DT AS createdDt
    FROM dbo.COMMUNITY c
    JOIN dbo.[USER] u ON u.ID = c.USER_ID
    <where>
        /* 숨김 처리되지 않은 게시글만 조회 (기본 조건) */
        c.IS_HIDDEN = 0
        /* 키워드 검색 조건이 있는 경우 추가 */
        <if test="keyword != null and keyword != ''">
            <choose>
                <when test="searchType == 'NICKNAME'">
                    AND CHARINDEX(#{keyword,jdbcType=NVARCHAR}, u.NICKNAME) > 0
                </when>
                <otherwise>
                    AND CHARINDEX(#{keyword,jdbcType=NVARCHAR}, c.TITLE) > 0
                </otherwise>
            </choose>
        </if>
    </where>
    ORDER BY c.CREATED_DT DESC, c.ID DESC
    OFFSET #{offset} ROWS
    FETCH NEXT #{size} ROWS ONLY
    </script>
    """)
    List<CommunityListResponse> findPage(
            @Param("searchType") String searchType,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size
    );


    /**
     * 게시글 ID로 상세 정보와 작성자 닉네임을 조회한다. 대상이 없으면 null을 반환한다.
     * */
    @Select("""
            SELECT
                c.ID AS id,
                c.USER_ID AS userId,
                u.NICKNAME AS nickname,
                c.TITLE AS title,
                c.CONTENT AS content,
                c.VIEW_COUNT AS viewCount,
                c.IS_HIDDEN AS isHidden,
                c.HIDDEN_DT AS hiddenDt,
                c.HIDDEN_BY AS hiddenBy,
                c.CREATED_DT AS createdDt,
                c.UPDATED_DT AS updatedDt
            FROM dbo.COMMUNITY c
            JOIN dbo.[USER] u ON u.ID = c.USER_ID
            WHERE c.ID = #{id}
            """)
    CommunityDetailResponse findCommunity(@Param("id") Long id);

    /**
     * 게시글을 등록한다. 조회수와 작성·수정 일시는 DB 기본값을 사용한다.
     * */
    @Insert("""
            INSERT INTO dbo.COMMUNITY (
                USER_ID,
                TITLE,
                CONTENT
            )
            VALUES (
                #{userId},
                #{request.title},
                #{request.content}
            )
            """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") CommunitySaveRequest request);

    /**
     * 게시글 ID와 작성자 ID가 일치하는 글의 제목·본문·수정 일시를 갱신한다.
     * */
    @Update("""
            UPDATE dbo.COMMUNITY
            SET TITLE = #{request.title},
                CONTENT = #{request.content},
                UPDATED_DT = SYSUTCDATETIME()
            WHERE ID = #{id}
              AND USER_ID = #{userId}
              AND IS_HIDDEN = 0
            """)
    int update(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("request") CommunitySaveRequest request
    );

    /**
     * 게시글 ID와 작성자 ID가 일치하는 글을 삭제하고 삭제된 행 수를 반환한다.
     * */
    @Delete("""
            DELETE FROM dbo.COMMUNITY
            WHERE ID = #{id}
              AND USER_ID = #{userId}
              AND IS_HIDDEN = 0
            """)
    int delete(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    /**
     * 게시글 조회수를 1 증가시키고 변경된 행 수를 반환한다.
     * */
    @Update("""
            UPDATE dbo.COMMUNITY
            SET VIEW_COUNT = VIEW_COUNT + 1
            WHERE ID = #{id}
            AND IS_HIDDEN = 0
    """)
    int increaseViewCount(@Param("id") Long id);


    /***
     * 같은 사용자의 기존 신고가 있는지 확인
     */

    @Select("""
        SELECT CASE
           WHEN EXISTS (
               SELECT 1 
               FROM dbo.COMMUNITY_REPORT
               WHERE REPORTER_ID = #{userId} 
                 AND COMMUNITY_ID = #{id}
           ) THEN 1 
           ELSE 0 
       END
        """)
    boolean existsReport(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    /**
     * 신고 등록
     * */
    @Insert("""
            INSERT INTO dbo.COMMUNITY_REPORT (
                COMMUNITY_ID,
                REPORTER_ID,
                REASON,
                DESCRIPTION
            )
            VALUES (
                #{id},
                #{userId},
                #{request.reason},
                #{request.description,jdbcType=NVARCHAR}
            )
            """)
    int insertReport(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("request") ReportSaveRequest request);

    /**
     * 특정 게시글의 전체 신고 이력 존재 여부 확인 (신고자/상태 조건 없이 체크)
     */
    @Select("""
            SELECT CASE
                       WHEN EXISTS (
                           SELECT 1
                           FROM dbo.COMMUNITY_REPORT
                           WHERE COMMUNITY_ID = #{id}
                       ) THEN 1
                       ELSE 0
                   END
            """)
    boolean existsReportHistory(@Param("id") Long id);
}
