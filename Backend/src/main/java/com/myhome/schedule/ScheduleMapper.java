package com.myhome.schedule;

import jakarta.validation.Valid;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScheduleMapper {

    /**
     * 현재 사용자의 일정 목록을 조회한다.
     * 미완료 일정을 먼저 표시하고, 마감일과 ID 순서로 정렬한다.
     * 컬럼 순서는 ScheduleResponse의 생성자 인자 순서에 맞춘다.
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
            ORDER BY IS_COMPLETED, DUE_DATE, ID
            """)
    List<ScheduleResponse> findSchedule(@Param("userId") Long userId);

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
