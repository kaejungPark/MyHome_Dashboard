package com.myhome.schedule;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ScheduleMapper {

    /**
     * 로그인한 사용자의 일정 중 조회 월과 기간이 겹치는 전체 개수를 조회한다.
     */
    @Select("""
            SELECT COUNT_BIG(*)
            FROM dbo.SCHEDULE
            WHERE USER_ID = #{userId}
            AND START_DATE <= EOMONTH(#{monthValid,jdbcType=DATE})
            AND DUE_DATE >= #{monthValid,jdbcType=DATE}
    """)
    long countAll(
            @Param("userId") Long userId,
            @Param("monthValid") LocalDate monthValid
    );

    /**
     * 조회 월과 기간이 겹치는 일정을 페이지 단위로 조회한다.
     * 미완료 일정부터 마감일과 ID 순으로 정렬한다.
     */
    @Select("""
            SELECT
                ID AS id,
                TITLE AS title,
                SCHEDULE_TYPE AS scheduleType,
                START_DATE AS startDate,
                DUE_DATE AS dueDate,
                IS_COMPLETED AS completed,
                AMOUNT AS amount,
                MEMO AS memo
            FROM dbo.SCHEDULE
            WHERE USER_ID = #{userId}
            AND START_DATE <= EOMONTH(#{monthValid,jdbcType=DATE})
            AND DUE_DATE >= #{monthValid,jdbcType=DATE}
            ORDER BY IS_COMPLETED, DUE_DATE, ID
            OFFSET #{offset} ROWS
            FETCH NEXT #{size} ROWS ONLY
    """)
    List<ScheduleResponse> findPage(
            @Param("userId") Long userId,
            @Param("monthValid") LocalDate monthValid,
            @Param("offset") long offset,
            @Param("size") int size);

    /**
     * 현재 사용자의 일정을 등록하고 저장된 행 수를 반환한다.
     * 날짜는 DATE 타입으로, 선택 입력인 금액은 DECIMAL 타입으로 전달한다.
     * 생성 일시는 현재 UTC 시각으로 지정하고, 수정 일시는 DB 기본값을 사용한다.
     */
    @Insert("""
            INSERT INTO dbo.SCHEDULE (
                USER_ID,
                TITLE,
                SCHEDULE_TYPE,
                START_DATE,
                DUE_DATE,
                IS_COMPLETED,
                AMOUNT,
                MEMO,
                CREATED_DT
            )
            VALUES (
                #{userId},
                #{request.title},
                #{request.scheduleType},
                #{request.startDate,jdbcType=DATE},
                #{request.dueDate,jdbcType=DATE},
                #{request.completed},
                #{request.amount,jdbcType=DECIMAL},
                #{request.memo},
                SYSUTCDATETIME()
            )
            """)
    int insert(@Param("userId") Long userId, @Param("request") ScheduleSaveRequest request);

    /**
     * ID와 사용자 ID가 일치하는 일정을 수정한다.
     * 생성 일시는 유지하고 수정 일시를 현재 UTC 시각으로 갱신한다.
     * 수정된 행 수를 반환한다.
     */
    @Update("""
            UPDATE dbo.SCHEDULE
            SET TITLE = #{request.title},
                SCHEDULE_TYPE = #{request.scheduleType},
                START_DATE = #{request.startDate,jdbcType=DATE},
                DUE_DATE = #{request.dueDate,jdbcType=DATE},
                IS_COMPLETED = #{request.completed},
                AMOUNT = #{request.amount,jdbcType=DECIMAL},
                MEMO = #{request.memo},
                UPDATED_DT = SYSUTCDATETIME()
            WHERE ID = #{id}
              AND USER_ID = #{userId}
            """)

    int update(@Param("id")Long id, @Param("userId") Long userId, @Param("request") ScheduleSaveRequest request);

    // 해당 사용자의 지출만 삭제하고, 삭제된 행 수를 반환한다.
    @Delete("""
        DELETE FROM dbo.SCHEDULE
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int delete(@Param("id") Long id, @Param("userId") Long userId);
}
