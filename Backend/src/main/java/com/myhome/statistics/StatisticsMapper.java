package com.myhome.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatisticsMapper {

    /**
     * 현재 사용자의 해당 월 지출을 카테고리별로 합산한다.
     * 조회 범위는 해당 월 1일 이상, 다음 달 1일 미만이다.
     */
    @Select("""
            SELECT
                c.ID AS categoryId,
                c.NAME AS categoryName,
                SUM(e.AMOUNT) AS amount
            FROM dbo.EXPENSE e
            INNER JOIN dbo.EXPENSE_CATEGORY c
                ON c.ID = e.CATEGORY_ID
            WHERE e.USER_ID = #{userId}
              AND e.EXPENSE_DATE >= #{monthStart,jdbcType=DATE}
              AND e.EXPENSE_DATE < #{nextMonthStart,jdbcType=DATE}
            GROUP BY c.ID, c.NAME
            ORDER BY SUM(e.AMOUNT) DESC, c.ID ASC
            """)
    List<CategoryTotal> findCategoryTotal(
            @Param("userId") Long userId,
            @Param("monthStart") LocalDate monthStart,
            @Param("nextMonthStart") LocalDate nextMonthStart
    );
}