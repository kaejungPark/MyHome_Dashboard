package com.myhome.dashborad;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface  DashboardMapper {

    /**
     * 기한이 지났거나 오늘부터 7일 뒤까지 마감되는 미완료 일정을 조회한다.
     * 마감일이 오래된 순서로 최대 3건을 반환한다.
     * 같은 마감일은 ID 순서로 정렬하여 조회 순서를 일정하게 유지한다.
     */
    @Select("""
        SELECT TOP (3)
            ID AS id,
            TITLE AS title,
            SCHEDULE_TYPE AS scheduleType,
            DUE_DATE AS dueDate
        FROM dbo.[SCHEDULE]
        WHERE USER_ID = #{userId}
          AND IS_COMPLETED = 0
          AND DUE_DATE <= #{endDate,jdbcType=DATE}
        ORDER BY DUE_DATE ASC, ID ASC
    """)
    List<DashboardScheduleResponse> findSchedules(
            @Param("userId") Long userId,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 기한이 지났거나 오늘부터 7일 뒤까지 마감되는 미완료 일정의 전체 건수를 조회한다.
     * 목록 조회와 동일한 조건을 사용하되 3건 제한은 적용하지 않는다.
     */
    @Select("""
        SELECT COUNT(*)
        FROM dbo.[SCHEDULE]
        WHERE USER_ID = #{userId}
          AND IS_COMPLETED = 0
          AND DUE_DATE <= #{endDate,jdbcType=DATE}
    """)
    int countSchedules(
            @Param("userId") Long userId,
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

    /**
     * 지정한 월에 적용되는 활성 고정 지출 중 납부 기록이 없는 항목을 조회한다.
     * 매월 납부일이 해당 월의 마지막 날을 넘으면 말일로 보정한다.
     * 보정된 납부일이 오늘부터 7일 뒤까지인 항목만 반환한다.
     * paymentMonth는 조회 대상 월의 1일이다.
     */
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

    /**
     * 보관 중인 식재료 중 소비기한이 지났거나 3일 이내인 항목을 최대 3건 조회한다.
     * ingredientEndDate는 서비스에서 계산한 오늘 기준 3일 뒤 날짜다.
     */
    @Select("""
        SELECT TOP (3)
            ID AS id,
            NAME AS name,
            STORAGE_LOCATION AS storageLocation,
            EXPIRATION_DATE AS expirationDate
        FROM dbo.INGREDIENT
        WHERE USER_ID = #{userId}
          AND STATUS = 'STORED'
          AND EXPIRATION_DATE IS NOT NULL
          AND EXPIRATION_DATE <= #{ingredientEndDate,jdbcType=DATE}
        ORDER BY EXPIRATION_DATE ASC, ID ASC
    """)
    List<DashboardIngredientResponse> findIngredient(
            @Param("userId") Long userId,
            @Param("ingredientEndDate") LocalDate ingredientEndDate
    );

    /**
     * 소비기한이 지났거나 3일 이내인 보관 중 식재료의 전체 건수를 조회한다.
     * 목록 조회와 동일한 조건을 사용하되 3건 제한은 적용하지 않는다.
     */
    @Select("""
        SELECT COUNT(*)
        FROM dbo.INGREDIENT
        WHERE USER_ID = #{userId}
          AND STATUS = 'STORED'
          AND EXPIRATION_DATE IS NOT NULL
          AND EXPIRATION_DATE <= #{ingredientEndDate,jdbcType=DATE}
    """)
    int countIngredients(
            @Param("userId") Long userId,
            @Param("ingredientEndDate") LocalDate ingredientEndDate
    );

    /**
     * 활성 집안일·소모품 중 예정일이 지났거나 7일 이내인 항목을 최대 3건 조회한다.
     * 예정일이 빠른 순서로 표시하며, 같은 예정일은 ID 순서로 정렬한다.
     * sevenEndDate는 서비스에서 계산한 오늘 기준 7일 뒤 날짜다.
     */
    @Select("""
        SELECT TOP (3)
            ID AS id,
            NAME AS name,
            TASK_TYPE AS taskType,
            NEXT_DUE_DATE AS nextDueDate
        FROM dbo.HOUSEHOLD_TASK
        WHERE USER_ID = #{userId}
            AND IS_ACTIVE = 1
            AND NEXT_DUE_DATE IS NOT NULL
            AND NEXT_DUE_DATE <= #{sevenEndDate,jdbcType=DATE}
        ORDER BY NEXT_DUE_DATE ASC, ID ASC
    """)
    List<DashboardHouseholdTaskResponse> findHouseholdTask(
            @Param("userId") Long userId,
            @Param("sevenEndDate")LocalDate sevenEndDate
    );

    /**
     * 예정일이 지났거나 7일 이내인 활성 집안일·소모품의 전체 건수를 조회한다.
     * 목록 조회와 동일한 조건을 사용하되 3건 제한은 적용하지 않는다.
     */
    @Select("""
        SELECT COUNT(*)
        FROM dbo.HOUSEHOLD_TASK
        WHERE USER_ID = #{userId}
            AND IS_ACTIVE = 1
            AND NEXT_DUE_DATE IS NOT NULL
            AND NEXT_DUE_DATE <= #{sevenEndDate,jdbcType=DATE}
    """)
    int countHouseholdTask(
            @Param("userId") Long userId,
            @Param("sevenEndDate")LocalDate sevenEndDate
    );
}
