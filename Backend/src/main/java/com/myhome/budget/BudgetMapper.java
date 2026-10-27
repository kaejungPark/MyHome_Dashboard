package com.myhome.budget;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 월 예산 테이블을 조회·변경하고 실제 지출 테이블에서 사용 금액을 합산한다.
 * 모든 요청은 사용자 ID로 범위를 제한한다.
 */
@Mapper
public interface BudgetMapper {

    /**
     * 사용자의 해당 월 예산 금액을 조회한다.
     * monthStart는 조회할 월의 1일이다.
     * 등록된 예산이 없으면 null을 반환하여 0원 예산과 구분한다.
     */
    @Select("""
            SELECT AMOUNT
            FROM dbo.MONTHLY_BUDGET
            WHERE USER_ID = #{userId}
              AND BUDGET_MONTH = #{monthStart}
            """)
    BigDecimal findAmount(@Param("userId")  Long userId, @Param("monthStart")LocalDate localDate);

    /**
     * 해당 월 1일 이상, 다음 달 1일 미만의 실제 지출을 합산한다.
     * 지출이 없으면 COALESCE로 null 대신 0을 반환한다.
     * 고정 지출 예정 금액이 아닌 EXPENSE에 등록된 금액을 기준으로 한다.
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
     * 반환값은 수정된 행 수이며 대상 예산이 없으면 0이다.
     */
    @Update("""
        UPDATE dbo.MONTHLY_BUDGET
        SET AMOUNT = #{amount},
            UPDATED_DT = SYSUTCDATETIME()
        WHERE USER_ID = #{userId}
          AND BUDGET_MONTH = #{monthStart}
        """)
    int update(@Param("userId") Long userId,@Param("monthStart") LocalDate monthStart,@Param("amount") BigDecimal amount);

    /**
     * 사용자 ID와 예산 월에 해당하는 예산을 삭제한다.
     * 삭제된 행 수를 반환하며, 실제 지출 내역은 유지한다.
     */
    @Delete("""
        DELETE FROM dbo.MONTHLY_BUDGET
        WHERE USER_ID = #{userId}
          AND BUDGET_MONTH = #{monthStart}
        """)
    int delete(@Param("userId") Long userId, @Param("monthStart") LocalDate monthStart);
}
