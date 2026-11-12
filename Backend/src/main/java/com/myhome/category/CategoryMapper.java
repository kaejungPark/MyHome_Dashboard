package com.myhome.category;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper {

    // 카테고리를 화면 표시 순서(SORT_ORDER)대로 조회한다.
    // 표시 순서가 같으면 ID가 작은 항목부터 보여준다.
    @Select("""
            SELECT
                ID AS id,
                NAME AS name,
                SORT_ORDER AS sortOrder
            FROM dbo.EXPENSE_CATEGORY
            ORDER BY SORT_ORDER, ID
            """)
    List<CategoryResponse> findAll();

    // 생활비 등록·수정 시 화면에서 보낸 카테고리 ID가 DB에 있는지 확인한다.
    // 조회 결과가 0이면 서비스에서 저장 요청을 거절한다.
    @Select("""
            SELECT COUNT(*)
            FROM dbo.EXPENSE_CATEGORY
            WHERE ID = #{categoryId}
            """)
    int countById(@Param("categoryId") Long categoryId);
}