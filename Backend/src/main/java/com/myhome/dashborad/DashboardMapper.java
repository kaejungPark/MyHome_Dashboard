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


    @Select("""
        SELECT
             r.ID,
                    r.TITLE,
                    r.AMOUNT,
                    d.paymentDate
                FROM dbo.RECURRING_EXPENSE r
                CROSS APPLY (
                    SELECT DATEFROMPARTS(
                       YEAR(#{paymentMonth}),
                		MONTH(#{paymentMonth}),
                        CASE
                            WHEN r.PAYMENT_DAY > DAY(EOMONTH(#{paymentMonth}))
                                THEN DAY(EOMONTH(#{paymentMonth}))
                            ELSE r.PAYMENT_DAY
                        END
                    ) AS paymentDate
                ) d
                LEFT JOIN dbo.RECURRING_PAYMENT p
                    ON p.RECURRING_EXPENSE_ID = r.ID
                   AND p.USER_ID = r.USER_ID
                   AND p.PAYMENT_MONTH = #{paymentMonth}
                WHERE r.USER_ID = #{userId}
                  AND r.IS_ACTIVE = 1
                  AND r.START_MONTH <= #{paymentMonth}
                  AND (r.END_MONTH IS NULL OR r.END_MONTH >= #{paymentMonth})
                  AND d.paymentDate BETWEEN #{today} AND DATEADD(DAY, 7, #{today})
                  AND p.ID IS NULL
                ORDER BY d.paymentDate, r.ID;
        """)
    List<UpcomingPaymentResponse> findPaymentByUserId(
            @Param("userId") Long userId,
            @Param("today")LocalDate today,
            @Param("paymentMonth") LocalDate paymentMonth
    );
}
