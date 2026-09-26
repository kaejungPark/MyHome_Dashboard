package com.myhome.budget;

import jakarta.validation.Valid;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface BudgetMapper {

    /**
     * 예산이 없으면 null을 반환한다. 설정된 0원과 구분한다.
     */
    @Select("""
            SELECT AMOUNT
            FROM dbo.MONTHLY_BUDGET
            WHERE USER_ID = #{userId}
              AND BUDGET_MONTH = #{monthStart}
            """)
    BigDecimal findAmount(@Param("userId")  Long userId, @Param("monthStart")LocalDate localDate);

    /**
     * 조회할 월의 1일 이상, 다음 달 1일 미만인 실제 지출을 합산한다.
     * 사용자 ID로 조회 범위를 제한하고, 지출이 없으면 0을 반환한다.
     */
    @Select("""
        SELECT COALESCE(SUM(AMOUNT), 0)
        FROM dbo.EXPENSE
        WHERE USER_ID = #{userId}
          AND EXPENSE_DATE >= #{monthStart}
          AND EXPENSE_DATE < #{nextMonthStart}
        """)
    BigDecimal sumExpenses(@Param("userId") Long userId, @Param("monthStart") LocalDate monthStart, @Param("nextMonthStart") LocalDate nextMonthStart);

    /**
     * 예산을 등록하고 저장된 행 수를 반환한다.
     * 생성·수정 일시는 DB의 기본값을 사용한다.
     */
    @Insert("""
            INSERT INTO dbo.MONTHLY_BUDGET(
                USER_ID,
                BUDGET_MONTH,
                AMOUNT
            )
            VALUES (
                #{userId},
                #{monthStart},
                #{amount}
            )
            """)
    int insert(@Param("userId") Long userId, @Param("monthStart") LocalDate monthStart, @Param("amount") BigDecimal amount);

    /**
     * 해당 사용자의 예산만 수정한다.
     * 생성 일시는 유지하고 수정 일시만 갱신한다.
     */
    @Update("""
        UPDATE dbo.MONTHLY_BUDGET
        SET AMOUNT = #{amount},
            UPDATED_DT = SYSUTCDATETIME()
        WHERE USER_ID = #{userId}
          AND BUDGET_MONTH = #{monthStart}
        """)
    int update(@Param("userId") Long userId,@Param("monthStart") LocalDate monthStart,@Param("amount") BigDecimal amount);

    // 해당 사용자의 지출만 삭제하고, 삭제된 행 수를 반환한다.
    @Delete("""
        DELETE FROM dbo.MONTHLY_BUDGET
        WHERE USER_ID = #{userId}
          AND BUDGET_MONTH = #{monthStart}
        """)
    int delete(@Param("userId") Long userId, @Param("monthStart") LocalDate monthStart);
}
