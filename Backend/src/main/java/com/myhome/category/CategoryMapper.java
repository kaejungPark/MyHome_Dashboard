package com.myhome.category;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper {

    // 표시 순서대로 카테고리 목록을 조회한다.
    @Select("""
            SELECT
                ID AS id,
                NAME AS name,
                SORT_ORDER AS sortOrder
            FROM dbo.EXPENSE_CATEGORY
            ORDER BY SORT_ORDER, ID
            """)
    List<CategoryResponse> findAll();

    // 카테고리가 존재하면 1, 없으면 0을 반환한다.
    @Select("""
            SELECT COUNT(*)
            FROM dbo.EXPENSE_CATEGORY
            WHERE ID = #{categoryId}
            """)
    int countById(@Param("categoryId") Long categoryId);
}