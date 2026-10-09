package com.myhome.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminCommunityMapper {

    /**
    * 상태 조건에 맞는 신고 내역 전체 수를 조회한다.
    * status가 ALL인 경우 전체 개수를 반환한다.
    */
    @Select("""
    <script>
    SELECT COUNT_BIG(*)
    FROM dbo.COMMUNITY_REPORT cr
    JOIN dbo.[USER] u ON u.ID = cr.REPORTER_ID
    <where>
        <if test="statusType != null and statusType != '' and statusType != 'ALL'">
            cr.STATUS = #{statusType, jdbcType=VARCHAR}
        </if>
    </where>
    </script>
    """)
    long countAll(
            @Param("statusType") String statusType
    );

    /**
     * 상태 조건에 맞는 신고 내역을 최신순으로 페이지 조회한다.
     * status가 ALL인 경우 전체 목록을 조회한다.
     */
    @Select("""
    <script>
    SELECT
        cr.ID AS id,
        cr.COMMUNITY_ID AS communityId,
        c.TITLE AS title,
        u.NICKNAME AS reporterNickname,
        cr.REASON AS reason,
        cr.STATUS AS status,
        cr.CREATED_DT AS createdDt
        FROM dbo.COMMUNITY_REPORT cr
        JOIN dbo.COMMUNITY c ON c.ID = cr.COMMUNITY_ID
        JOIN dbo.[USER] u ON u.ID = cr.REPORTER_ID
    <where>
        <if test="statusType != null and statusType != '' and statusType != 'ALL'">
            cr.STATUS = #{statusType, jdbcType=VARCHAR}
        </if>
    </where>
    ORDER BY cr.CREATED_DT DESC, cr.ID DESC
    OFFSET #{offset} ROWS
    FETCH NEXT #{size} ROWS ONLY
    </script>
    """)
    List<AdminReportListResponse> findPage(
            @Param("statusType") String statusType,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 신고 내용, 대상 게시글, 신고자·작성자와 처리 이력을 조회한다.
     * 관리자 조회이므로 숨김 여부와 신고 상태에 따른 제한을 두지 않는다.
     */
    @Select("""
        SELECT
            cr.ID AS id,
            cr.COMMUNITY_ID AS communityId,
            c.TITLE AS title,
            reporter.NICKNAME AS reporterNickname,
            cr.REASON AS reason,
            cr.STATUS AS status,
            cr.CREATED_DT AS createdDt,
            cr.DESCRIPTION AS description,
            c.CONTENT AS content,
            author.NICKNAME AS authorNickname,
            c.IS_HIDDEN AS isHidden,
            cr.PROCESSED_BY AS processedBy,
            cr.PROCESSED_DT AS processedDt,
            cr.PROCESS_NOTE AS processNote
        FROM dbo.COMMUNITY_REPORT cr
        JOIN dbo.COMMUNITY c ON c.ID = cr.COMMUNITY_ID
        JOIN dbo.[USER] reporter ON reporter.ID = cr.REPORTER_ID
        JOIN dbo.[USER] author ON author.ID = c.USER_ID
        WHERE cr.ID = #{id}
        """)
    AdminReportDetailResponse findReport(@Param("id") Long id);

    /**
     * 처리 대기 신고에만 처리 결과와 관리자 정보를 저장한다.
     * 이미 처리된 신고는 변경하지 않고 0을 반환한다.
     */
    @Update("""
        UPDATE dbo.COMMUNITY_REPORT
        SET STATUS = #{request.status},
            PROCESSED_BY = #{adminId},
            PROCESSED_DT = SYSUTCDATETIME(),
            PROCESS_NOTE = #{request.processNote, jdbcType=NVARCHAR}
        WHERE ID = #{id}
          AND STATUS = 'PENDING'
        """)
    int processReport(
            @Param("adminId") Long adminId,
            @Param("id") Long id,
            @Param("request") AdminReportProcessRequest request
    );

    /**
     * 신고 대상 게시글을 숨긴다.
     * 이미 숨겨진 게시글이면 기존 숨김 처리자와 처리일을 유지한다.
     */
    @Update("""
        UPDATE dbo.COMMUNITY
        SET HIDDEN_BY = CASE
                WHEN IS_HIDDEN = 0 THEN #{adminId}
                ELSE HIDDEN_BY
            END,
            HIDDEN_DT = CASE
                WHEN IS_HIDDEN = 0 THEN SYSUTCDATETIME()
                ELSE HIDDEN_DT
            END,
            IS_HIDDEN = 1
        WHERE ID = #{communityId}
        """)
    int hideCommunity(
            @Param("adminId") Long adminId,
            @Param("communityId") Long communityId
    );
}
