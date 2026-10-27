package com.myhome.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 회원·신고·게시글 테이블에서 관리자 통계를 조회한다.
 * COUNT_BIG으로 조회한 건수는 Java의 long으로 받는다.
 */
@Mapper
public interface AdminDashboardMapper {

    /** 탈퇴 상태를 제외한 전체 회원 수를 조회한다. */
    @Select("""
       SELECT COUNT_BIG(*)
       FROM dbo.[USER]
       WHERE STATUS <> 'WITHDRAWN'
    """)
    long getTotalUserCount();

    /** UTC로 전달받은 시작 시각 이상, 종료 시각 미만에 가입한 회원 수를 조회한다. */
    @Select("""
       SELECT COUNT_BIG(*)
       FROM dbo.[USER]
       WHERE CREATED_DT >= #{startUtc,jdbcType=TIMESTAMP}
       AND CREATED_DT < #{endUtc,jdbcType=TIMESTAMP}
    """)
    long getTodaySignupCount(
            @Param("startUtc") LocalDateTime startUtc,
            @Param("endUtc") LocalDateTime endUtc
    );

    /** 작성자의 계정 상태와 관계없이 처리 대기 중인 신고 수를 조회한다. */
    @Select("""
       SELECT COUNT_BIG(*)
       FROM dbo.[COMMUNITY_REPORT]
       WHERE STATUS = 'PENDING'
    """)
    long getPendingReportCount();

    /** 작성자의 계정 상태와 관계없이 숨김 처리된 게시글 수를 조회한다. */
    @Select("""
       SELECT COUNT_BIG(*)
       FROM dbo.[COMMUNITY]
       WHERE IS_HIDDEN = 1
    """)
    long getHiddenCommunityCount();

    /**
     * 지정한 UTC 범위의 가입자를 한국 날짜별로 집계한다.
     * 현재 계정 상태와 관계없이 가입 당시의 기록을 기준으로 계산한다.
     * 가입자가 없는 날짜는 서비스에서 0명으로 보완한다.
     */
    @Select("""
    SELECT
        CAST(DATEADD(HOUR, 9, CREATED_DT) AS DATE) AS [date],
        COUNT_BIG(*) AS signupCount
    FROM dbo.[USER]
    WHERE CREATED_DT >= #{startUtc,jdbcType=TIMESTAMP}
      AND CREATED_DT < #{endUtc,jdbcType=TIMESTAMP}
    GROUP BY CAST(DATEADD(HOUR, 9, CREATED_DT) AS DATE)
    ORDER BY [date] ASC
    """)
    List<AdminSignupTrendResponse> findSignupTrend(
            @Param("startUtc") LocalDateTime startUtc,
            @Param("endUtc") LocalDateTime endUtc
    );
}
