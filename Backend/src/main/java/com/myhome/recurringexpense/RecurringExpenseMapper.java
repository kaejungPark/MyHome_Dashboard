package com.myhome.recurringexpense;

import jakarta.validation.Valid;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RecurringExpenseMapper {

    /**
     * 현재 사용자의 고정 지출 목록을 조회한다.
     * 카테고리 테이블과 조인하여 카테고리명을 함께 반환한다.
     * 시작·종료 월은 응답 형식에 맞춰 YYYY-MM 문자열로 변환한다.
     * 비활성 항목도 포함하며, 납부일과 ID 순서로 정렬한다.
     * SELECT 컬럼 순서는 RecurringExpenseResponse의 생성자 인자 순서에 맞춘다.
     */

    @Select("""
        SELECT
            r.ID AS id,
            r.CATEGORY_ID AS categoryId,
            c.NAME AS categoryName,
            r.TITLE AS title,
            r.AMOUNT AS amount,
            r.PAYMENT_DAY AS paymentDay,
            r.PAYMENT_METHOD AS paymentMethod,
            CONVERT(VARCHAR(7), r.START_MONTH, 120) AS startMonth,
            CONVERT(VARCHAR(7), r.END_MONTH, 120) AS endMonth,
            r.IS_ACTIVE AS active,
            r.MEMO AS memo
        FROM dbo.RECURRING_EXPENSE r
        JOIN dbo.EXPENSE_CATEGORY c ON c.ID = r.CATEGORY_ID
        WHERE r.USER_ID = #{userId}
        ORDER BY r.PAYMENT_DAY, r.ID
        """)
    List<RecurringExpenseResponse> findeRecurringExpense(@Param("userId") Long userId);

    /**
     * 고정 지출을 등록하고 저장된 행 수를 반환한다.
     * 생성·수정 일시는 DB의 기본값을 사용한다.
     */
    @Insert("""
       INSERT INTO dbo.RECURRING_EXPENSE (
            USER_ID,
            CATEGORY_ID,
            TITLE,
            AMOUNT,
            PAYMENT_DAY,
            PAYMENT_METHOD,
            START_MONTH,
            END_MONTH,
            IS_ACTIVE,
            MEMO,
            CREATED_DT
       )
       VALUES (
            #{userId},
            #{request.categoryId},
            #{request.title},
            #{request.amount},
            #{request.paymentDay},
            #{request.paymentMethod},
            #{startMonth,jdbcType=DATE},
            #{endMonth,jdbcType=DATE},
            #{request.active},
            #{request.memo},
            SYSUTCDATETIME()
       )
       """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") RecurringExpenseSaveRequest request,
            @Param("startMonth") LocalDate startMonth,
            @Param("endMonth") LocalDate endMonth
    );

    /**
     * 고정 지출을 수정하고 저장된 행 수를 반환한다.
     * 생성·수정 일시는 DB의 기본값을 사용한다.
     */
    @Update("""
        UPDATE dbo.RECURRING_EXPENSE
        SET CATEGORY_ID = #{request.categoryId},
            TITLE = #{request.title},
            AMOUNT = #{request.amount},
            PAYMENT_DAY = #{request.paymentDay},
            PAYMENT_METHOD = #{request.paymentMethod},
            START_MONTH = #{startMonth,jdbcType=DATE},
            END_MONTH = #{endMonth,jdbcType=DATE},
            IS_ACTIVE = #{request.active},
            MEMO = #{request.memo},
            UPDATED_DT = SYSUTCDATETIME()
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int update(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("request") RecurringExpenseSaveRequest request,
               @Param("startMonth") LocalDate startMonth,
               @Param("endMonth") LocalDate endMonth);

    // 해당 사용자의 고정 지출만 삭제하고, 삭제된 행 수를 반환한다.
    @Delete("""
        DELETE FROM dbo.RECURRING_EXPENSE
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int delete(@Param("id") Long id, @Param("userId") Long userId);
}
