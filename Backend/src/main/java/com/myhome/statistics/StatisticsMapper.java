package com.myhome.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatisticsMapper {

    /**
     * 사용자가 조회 월에 등록한 지출을 카테고리별로 합산한다.
     * 날짜는 해당 월 1일부터 다음 달 1일 직전까지 조회한다.
     * 지출이 있는 카테고리만 반환하며, 합계가 큰 순서로 정렬한다.
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