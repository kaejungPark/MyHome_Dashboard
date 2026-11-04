package com.myhome.recurringexpense;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

/**
 * 고정 지출 설정, 월별 납부 기록과 연결된 생활비를 조회·저장한다.
 * RECURRING_EXPENSE는 반복 설정, RECURRING_PAYMENT는 월별 납부 기록,
 * EXPENSE는 실제 생활비를 보관한다.
 */
@Mapper
public interface RecurringExpenseMapper {

    /**
     * 로그인한 사용자의 고정 지출 중 검색 조건에 맞는 전체 건수를 조회한다.
     * findPage와 동일한 조건을 사용하여 목록과 전체 페이지 수를 일치시킨다.
     */
    @Select("""
        <script>
        SELECT COUNT_BIG(*)
        FROM dbo.RECURRING_EXPENSE r
        JOIN dbo.EXPENSE_CATEGORY c
            ON c.ID = r.CATEGORY_ID
        WHERE r.USER_ID = #{userId}
         <if test="searchTitle != null and searchTitle != ''">
            AND r.TITLE LIKE CONCAT('%', #{searchTitle}, '%')
         </if>
         <if test="categoryId != null">
            AND r.CATEGORY_ID = #{categoryId}
         </if>
         <if test="active != null">
             AND r.IS_ACTIVE = #{active}
         </if>
        </script>
    """)
    long countAll(
            @Param("userId") Long userId,
            @Param("searchTitle") String searchTitle,
            @Param("categoryId") Long categoryId,
            @Param("active") Boolean active
    );

    /**
     * 검색 조건에 맞는 본인의 고정 지출을 페이지 단위로 조회한다.
     * 납부일과 ID를 기준으로 오름차순 정렬한다.
     */
    @Select("""
        <script>
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
         <if test="searchTitle != null and searchTitle != ''">
            AND r.TITLE LIKE CONCAT('%', #{searchTitle}, '%')
         </if>
         <if test="categoryId != null">
            AND r.CATEGORY_ID = #{categoryId}
         </if>
         <if test="active != null">
             AND r.IS_ACTIVE = #{active}
         </if>
        ORDER BY r.PAYMENT_DAY, r.ID
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
        </script>
    """)
    List<RecurringExpenseResponse> findPage(
            @Param("userId") Long userId,
            @Param("searchTitle") String searchTitle,
            @Param("categoryId") Long categoryId,
            @Param("active") Boolean active,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 조회 월의 납부 목록에 표시할 전체 항목 수를 조회한다.
     * 사용 중이면서 적용 기간에 해당하는 항목을 포함한다.
     * 해당 월의 납부 기록이 있으면 현재 비활성 상태이거나 적용 기간이 변경되어도 포함한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.RECURRING_EXPENSE r
        JOIN dbo.EXPENSE_CATEGORY c
          ON c.ID = r.CATEGORY_ID
        LEFT JOIN dbo.RECURRING_PAYMENT p
          ON p.RECURRING_EXPENSE_ID = r.ID
         AND p.USER_ID = r.USER_ID
         AND p.PAYMENT_MONTH = #{monthStart}
        WHERE r.USER_ID = #{userId}
          AND (
              p.ID IS NOT NULL
              OR (
                  r.IS_ACTIVE = 1
                  AND r.START_MONTH <= #{monthStart}
                  AND (
                      r.END_MONTH IS NULL
                      OR r.END_MONTH >= #{monthStart}
                  )
              )
          )
    """)
    long countMonthly(
            @Param("userId") Long userId,
            @Param("monthStart") LocalDate monthStart
    );

    /**
     * 조회 월의 전체 대상에 현재 설정된 예정 금액을 합산한다.
     * 납부 완료 항목도 포함하며, 현재 페이지의 합계나 미납 잔액이 아니다.
     * 실제 납부 당시 금액이 아닌 고정 지출 설정의 금액을 기준으로 계산한다.
     * 대상이 없으면 0을 반환한다.
     */
    @Select("""
        SELECT COALESCE(SUM(r.AMOUNT), 0)
        FROM dbo.RECURRING_EXPENSE r
        JOIN dbo.EXPENSE_CATEGORY c
          ON c.ID = r.CATEGORY_ID
        LEFT JOIN dbo.RECURRING_PAYMENT p
          ON p.RECURRING_EXPENSE_ID = r.ID
         AND p.USER_ID = r.USER_ID
         AND p.PAYMENT_MONTH = #{monthStart}
        WHERE r.USER_ID = #{userId}
          AND (
              p.ID IS NOT NULL
              OR (
                  r.IS_ACTIVE = 1
                  AND r.START_MONTH <= #{monthStart}
                  AND (
                      r.END_MONTH IS NULL
                      OR r.END_MONTH >= #{monthStart}
                  )
              )
          )
    """)
    BigDecimal sumMonthlyAmount(
            @Param("userId") Long userId,
            @Param("monthStart") LocalDate monthStart
    );

    /**
     * 조회 월의 대상 목록에 납부 기록과 실제 생활비 정보를 연결한다.
     * 납부 기록 유무로 완료 여부를 판단하고, 생활비에서 실제 납부일을 가져온다.
     * 설정된 납부일이 해당 월에 없으면 말일로 보정한다.
     * 예: 납부일이 31일이면 4월에는 30일을 예정일로 사용한다.
     * 보정된 예정일과 ID 순서로 정렬하여 페이지 단위로 조회한다.
     */
    @Select("""
        SELECT
            r.ID AS id,
            r.CATEGORY_ID AS categoryId,
            c.NAME AS categoryName,
            r.TITLE AS title,
            r.AMOUNT AS amount,
            DATEADD(
                DAY,
                CASE
                    WHEN r.PAYMENT_DAY > DAY(EOMONTH(CAST(#{monthStart} AS DATE)))
                        THEN DAY(EOMONTH(CAST(#{monthStart} AS DATE)))
                    ELSE r.PAYMENT_DAY
                END - 1,
                CAST(#{monthStart} AS DATE)
            ) AS paymentDate,
            r.PAYMENT_METHOD AS paymentMethod,
            CAST(
                CASE WHEN p.ID IS NOT NULL THEN 1 ELSE 0 END
                AS BIT
            ) AS paid,
            p.EXPENSE_ID AS expenseId,
            e.EXPENSE_DATE AS paidDate
        FROM dbo.RECURRING_EXPENSE r
        JOIN dbo.EXPENSE_CATEGORY c
          ON c.ID = r.CATEGORY_ID
        LEFT JOIN dbo.RECURRING_PAYMENT p
          ON p.RECURRING_EXPENSE_ID = r.ID
         AND p.USER_ID = r.USER_ID
         AND p.PAYMENT_MONTH = #{monthStart}
        LEFT JOIN dbo.EXPENSE e
          ON e.ID = p.EXPENSE_ID
         AND e.USER_ID = p.USER_ID
        WHERE r.USER_ID = #{userId}
          AND (
              p.ID IS NOT NULL
              OR (
                  r.IS_ACTIVE = 1
                  AND r.START_MONTH <= #{monthStart}
                  AND (
                      r.END_MONTH IS NULL
                      OR r.END_MONTH >= #{monthStart}
                  )
              )
          )
        ORDER BY paymentDate ASC, r.ID ASC
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
    """)
    List<MonthlyPaymentItem> findMonthlyPage(
            @Param("userId") Long userId,
            @Param("monthStart") LocalDate monthStart,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 현재 사용자의 고정 지출을 등록하고 저장된 행 수를 반환한다.
     * 시작·종료 월은 Service에서 변환한 LocalDate를 사용한다.
     * 종료 월이 null이어도 날짜 타입으로 전달되도록 jdbcType=DATE를 지정한다.
     * 생성 일시는 SQL에서 지정하고, 수정 일시는 DB 기본값을 사용한다.
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
     * ID와 사용자 ID가 모두 일치하는 고정 지출을 수정한다.
     * 생성 일시는 유지하고 수정 일시만 현재 UTC 시각으로 갱신한다.
     * 수정된 행 수를 반환한다.
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

    /**
     * 로그인한 사용자 소유의 고정 지출 한 건을 조회한다.
     * 대상이 없거나 다른 사용자 소유이면 null을 반환한다.
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
    WHERE r.ID = #{id}
      AND r.USER_ID = #{userId}
    """)
    RecurringExpenseResponse findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    /**
     * 해당 사용자의 고정 지출에 월별 납부 기록이 있으면 true를 반환한다.
     */
    @Select("""
    SELECT CAST(
        CASE WHEN EXISTS (
            SELECT 1
            FROM dbo.RECURRING_PAYMENT
            WHERE USER_ID = #{userId}
              AND RECURRING_EXPENSE_ID = #{id}
              AND PAYMENT_MONTH = #{paymentMonth}
        ) THEN 1 ELSE 0 END
        AS BIT
    )
    """)
    boolean existsPayment(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("paymentMonth") LocalDate paymentMonth
    );

    /**
     * 고정 지출 설정을 복사하여 FIXED 유형의 실제 생활비를 생성한다.
     * OUTPUT INSERTED.ID로 생성된 생활비 ID를 반환한다.
     * 지출일은 납부 대상 월이 아니라 요청에서 받은 실제 납부일이다.
     * 결과값을 받는 변경 SQL이므로 @Select와 affectData=true를 함께 사용한다.
     */
    @Select(value = """
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
    OUTPUT INSERTED.ID
    VALUES (
        #{userId},
        #{expense.categoryId},
        #{expense.amount},
        #{paymentDate},
        'FIXED',
        #{expense.paymentMethod,jdbcType=NVARCHAR},
        #{expense.title},
        #{expense.memo,jdbcType=NVARCHAR}
    )
    """, affectData = true)
    @Options(
            flushCache = Options.FlushCachePolicy.TRUE,
            useCache = false
    )
    Long insertPaidExpense(
            @Param("userId") Long userId,
            @Param("expense") RecurringExpenseResponse expense,
            @Param("paymentDate") LocalDate paymentDate
    );

    /**
     * 고정 지출의 납부 월과 자동 생성된 생활비를 연결한다.
     * 같은 고정 지출·월의 중복은 DB UNIQUE 제약조건으로 차단한다.
     */
    @Insert("""
    INSERT INTO dbo.RECURRING_PAYMENT (
        USER_ID,
        RECURRING_EXPENSE_ID,
        PAYMENT_MONTH,
        EXPENSE_ID
    )
    VALUES (
        #{userId},
        #{id},
        #{paymentMonth},
        #{expenseId}
    )
    """)
    int insertPayment(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("paymentMonth") LocalDate paymentMonth,
            @Param("expenseId") Long expenseId
    );

    /**
     * 해당 월에 실제 납부 기록이 있는 항목과 연결된 생활비 정보를 조회한다.
     * 월별 예정 목록과 달리 미납 항목은 포함하지 않는다.
     */
    @Select("""
    SELECT
        p.RECURRING_EXPENSE_ID AS recurringExpenseId,
        p.EXPENSE_ID AS expenseId,
        e.EXPENSE_DATE AS paidDate
    FROM dbo.RECURRING_PAYMENT p
    JOIN dbo.EXPENSE e
      ON e.ID = p.EXPENSE_ID
     AND e.USER_ID = p.USER_ID
    WHERE p.USER_ID = #{userId}
      AND p.PAYMENT_MONTH = #{paymentMonth}
    """)
    List<RecurringPaymentResponse> findPaymentsByMonth(
            @Param("userId") Long userId,
            @Param("paymentMonth") LocalDate paymentMonth
    );

    /**
     * 본인의 해당 월 납부 기록을 삭제하고 연결되어 있던 생활비 ID를 반환한다.
     * OUTPUT DELETED.EXPENSE_ID로 삭제한 행의 값을 받아 다음 생활비 삭제에 사용한다.
     * 조건에 맞는 납부 기록이 없으면 null을 반환한다.
     * 결과값을 받는 변경 SQL이므로 @Select와 affectData=true를 함께 사용한다.
     */
    @Select(value = """
    DELETE FROM dbo.RECURRING_PAYMENT
    OUTPUT DELETED.EXPENSE_ID
    WHERE USER_ID = #{userId}
      AND RECURRING_EXPENSE_ID = #{id}
      AND PAYMENT_MONTH = #{paymentMonth}
    """, affectData = true)
    @Options(
            flushCache = Options.FlushCachePolicy.TRUE,
            useCache = false
    )
    Long deletePayment(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("paymentMonth") LocalDate paymentMonth
    );

    /**
     * 납부 취소한 기록에 연결된 현재 사용자의 생활비를 삭제한다.
     * */
    @Delete("""
    DELETE FROM dbo.EXPENSE
    WHERE ID = #{expenseId}
      AND USER_ID = #{userId}
    """)
    int deletePaidExpense(
            @Param("userId") Long userId,
            @Param("expenseId") Long expenseId
    );

    /**
     * 현재 사용자의 고정 지출에 납부 기록이 하나라도 있는지 확인한다.
     * */
    @Select("""
    SELECT CAST(
        CASE WHEN EXISTS (
            SELECT 1
            FROM dbo.RECURRING_PAYMENT
            WHERE USER_ID = #{userId}
              AND RECURRING_EXPENSE_ID = #{id}
        ) THEN 1 ELSE 0 END
        AS BIT
    )
    """)
    boolean existsPaymentHistory(
            @Param("userId") Long userId,
            @Param("id") Long id
    );



}
