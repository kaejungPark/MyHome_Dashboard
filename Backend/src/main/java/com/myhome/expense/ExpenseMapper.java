package com.myhome.expense;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface ExpenseMapper {

    /**
     * 사용자별 지출을 날짜가 최신인 순서로 조회한다.
     * 같은 날짜라면 ID가 큰 항목을 먼저 표시한다.
     */
    @Select("""
        SELECT
            e.ID AS id,
            e.CATEGORY_ID AS categoryId,
            c.NAME AS categoryName,
            e.AMOUNT AS amount,
            e.EXPENSE_DATE AS expenseDate,
            e.EXPENSE_TYPE AS expenseType,
            e.PAYMENT_METHOD AS paymentMethod,
            e.TITLE AS title,
            e.MEMO AS memo
        FROM dbo.EXPENSE e
        INNER JOIN dbo.EXPENSE_CATEGORY c
            ON c.ID = e.CATEGORY_ID
        WHERE e.USER_ID = #{userId}
        ORDER BY e.EXPENSE_DATE DESC, e.ID DESC
        """)
    List<ExpenseResponse> findAllByUserId(
            @Param("userId") Long userId
    );

    /**
     * 등록 요청의 카테고리가 실제로 존재하는지 확인한다.
     */
    @Select("""
        SELECT COUNT(*)
        FROM dbo.EXPENSE_CATEGORY
        WHERE ID = #{categoryId}
        """)
    int countById(@Param("categoryId") Long categoryId);

    /**
     * 지출을 등록하고 저장된 행 수를 반환한다.
     * 생성·수정 일시는 DB의 기본값을 사용한다.
     */
    @Insert("""
        INSERT INTO dbo.EXPENSE (
            USER_ID,
            CATEGORY_ID,
            AMOUNT,
            EXPENSE_DATE,
            EXPENSE_TYPE,
            PAYMENT_METHOD,
            TITLE,
            MEMO
        )
        VALUES (
            #{userId},
            #{request.categoryId},
            #{request.amount},
            #{request.expenseDate},
            #{request.expenseType},
            #{request.paymentMethod},
            #{request.title},
            #{request.memo}
        )
        """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") ExpenseCreateRequest request
    );

    /**
     * 해당 사용자의 지출만 수정한다.
     * 생성 일시는 유지하고 수정 일시만 갱신한다.
     */
    @Update("""
        UPDATE dbo.EXPENSE
        SET CATEGORY_ID = #{request.categoryId},
            AMOUNT = #{request.amount},
            EXPENSE_DATE = #{request.expenseDate},
            EXPENSE_TYPE = #{request.expenseType},
            PAYMENT_METHOD = #{request.paymentMethod},
            TITLE = #{request.title},
            MEMO = #{request.memo},
            UPDATED_DT = SYSUTCDATETIME()
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int update(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("request") ExpenseUpdateRequest request
    );

    // 해당 사용자의 지출만 삭제하고, 삭제된 행 수를 반환한다.
    @Delete("""
        DELETE FROM dbo.EXPENSE
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int deleteByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}