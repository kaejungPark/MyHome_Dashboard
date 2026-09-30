package com.myhome.dashborad;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface  DashboardMapper {

    // 오늘부터 30일 뒤까지 마감되는 미완료 일정을 최대 5개 조회한다.
    @Select("""
        SELECT TOP (5)
            ID AS id,
            TITLE AS title,
            SCHEDULE_TYPE AS scheduleType,
            DUE_DATE AS dueDate
        FROM dbo.[SCHEDULE]
        WHERE USER_ID = #{userId}
          AND IS_COMPLETED = 0
          AND DUE_DATE BETWEEN #{today,jdbcType=DATE} AND #{endDate,jdbcType=DATE}
        ORDER BY DUE_DATE ASC, ID ASC
        """)
    List<DashboardScheduleResponse> findSchedules(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("endDate") LocalDate endDate
    );

    // 오늘부터 30일 뒤까지 보증이 종료되는 미처분 물품을 최대 5개 조회한다.
    @Select("""
        SELECT TOP (5)
            ID AS id,
            NAME AS name,
            WARRANTY_END_DATE AS warrantyEndDate
        FROM dbo.[HOME_ITEM]
        WHERE USER_ID = #{userId}
            AND STATUS <> 'DISPOSED'
            AND WARRANTY_END_DATE BETWEEN #{today,jdbcType=DATE} AND #{endDate,jdbcType=DATE}
        ORDER BY WARRANTY_END_DATE ASC, ID ASC
        """)
    List<DashboardItemResponse> findItems(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("endDate") LocalDate endDate);
}
