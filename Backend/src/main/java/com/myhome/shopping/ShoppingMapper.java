package com.myhome.shopping;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ShoppingMapper {

    // 사용자와 검색 조건에 일치하는 전체 장보기 항목 수를 조회한다.
    @Select("""
        <script>
        SELECT COUNT_BIG(*)
        FROM dbo.SHOPPING_ITEM s
        WHERE s.USER_ID = #{userId}
         <if test="searchName != null and searchName != ''">
            AND s.NAME LIKE CONCAT('%', #{searchName}, '%')
         </if>
         <if test="isCompleted != null">
            AND s.IS_COMPLETED = #{isCompleted}
         </if>
         <if test="searchDate != null">
            AND s.PURCHASE_DUE_DATE = #{searchDate}
         </if>
        </script>
    """)
    long countAll(
            @Param("userId")Long userId,
            @Param("searchName")String searchName,
            @Param("isCompleted")Boolean isCompleted,
            @Param("searchDate") LocalDate searchDate
    );

    // countAll과 동일한 조건으로 현재 페이지의 장보기 항목을 조회한다.
    @Select("""
        <script>
        SELECT
            s.ID AS id,
            s.NAME AS name,
            s.QUANTITY AS quantity,
            s.UNIT AS unit,
            s.IS_COMPLETED AS isCompleted,
            s.COMPLETED_DATE AS completedDate,
            s.PURCHASE_DUE_DATE AS purchaseDueDate,
            s.MEMO AS memo
        FROM dbo.SHOPPING_ITEM s
        WHERE s.USER_ID = #{userId}
         <if test="searchName != null and searchName != ''">
            AND s.NAME LIKE CONCAT('%', #{searchName}, '%')
         </if>
         <if test="isCompleted != null">
            AND s.IS_COMPLETED = #{isCompleted}
         </if>
         <if test="searchDate != null">
            AND s.PURCHASE_DUE_DATE = #{searchDate}
         </if>
        ORDER BY s.CREATED_DT DESC, s.ID DESC
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
        </script>
    """)
    List<ShoppingResponse> findPage(
            @Param("userId")Long userId,
            @Param("searchName")String searchName,
            @Param("isCompleted")Boolean isCompleted,
            @Param("searchDate")LocalDate searchDate,
            @Param("offset") long offset,
            @Param("size") int size
    );

    // 장보기 항목을 등록하며, 구매 여부와 생성·수정 시각은 DB 기본값을 사용한다.
    @Insert("""
        INSERT INTO dbo.SHOPPING_ITEM (
            USER_ID,
            NAME,
            QUANTITY,
            UNIT,
            PURCHASE_DUE_DATE,
            MEMO
        )
        VALUES (
            #{userId},
            #{request.name},
            #{request.quantity},
            #{request.unit},
            #{request.purchaseDueDate,jdbcType=DATE},
            #{request.memo,jdbcType=NVARCHAR}
        )
    """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") ShoppingSaveRequest request
    );

    // 항목 ID와 사용자 ID가 모두 일치하는 항목의 입력 정보와 수정 시각을 갱신한다.
    @Update("""
       UPDATE dbo.SHOPPING_ITEM
       SET NAME = #{request.name},
           QUANTITY = #{request.quantity},
           UNIT = #{request.unit},
           PURCHASE_DUE_DATE = #{request.purchaseDueDate,jdbcType=DATE},
           MEMO = #{request.memo,jdbcType=NVARCHAR},
           UPDATED_DT = SYSUTCDATETIME()
       WHERE ID = #{id}
           AND USER_ID = #{userId}
    """)
    int update(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("request") ShoppingSaveRequest request
    );

    // 항목 ID와 사용자 ID가 모두 일치하는 장보기 항목을 삭제한다.
    @Delete("""
        DELETE FROM dbo.SHOPPING_ITEM
        WHERE ID = #{id}
          AND USER_ID = #{userId}
    """)
    int delete(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    // 구매 상태와 관계없이 사용자 소유의 장보기 항목을 조회한다.
    @Select("""
    SELECT
        ID AS id,
        NAME AS name,
        QUANTITY AS quantity,
        UNIT AS unit,
        IS_COMPLETED AS isCompleted,
        COMPLETED_DATE AS completedDate,
        PURCHASE_DUE_DATE AS purchaseDueDate,
        MEMO AS memo
    FROM dbo.SHOPPING_ITEM
    WHERE ID = #{id}
      AND USER_ID = #{userId}
    """)
    ShoppingResponse findById(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    // 미구매 항목만 완료 처리하고 실제 구매일과 수정 시각을 저장한다.
    @Update("""
    UPDATE dbo.SHOPPING_ITEM
    SET IS_COMPLETED = 1,
        COMPLETED_DATE = #{completedDate,jdbcType=DATE},
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{id}
      AND USER_ID = #{userId}
      AND IS_COMPLETED = 0
    """)
    int shoppingCompleted(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("completedDate") LocalDate completedDate
    );

    // 구매 완료 항목만 미구매로 변경하고 실제 구매일을 초기화하며 수정 시각을 갱신한다.
    @Update("""
    UPDATE dbo.SHOPPING_ITEM
    SET IS_COMPLETED = 0,
        COMPLETED_DATE = NULL,
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{id}
      AND USER_ID = #{userId}
      AND IS_COMPLETED = 1
    """)
    int shoppingCancel(
            @Param("userId") Long userId,
            @Param("id") Long id
    );
}
