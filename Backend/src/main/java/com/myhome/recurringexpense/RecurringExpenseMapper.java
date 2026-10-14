package com.myhome.recurringexpense;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

@Mapper
public interface RecurringExpenseMapper {

    /**
     * 로그인한 사용자의 전체 고정 지출 개수를 조회한다.
     * 페이지 수가 목록과 일치하도록 findPage와 동일한 조회 조건을 사용한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.RECURRING_EXPENSE r
        JOIN dbo.EXPENSE_CATEGORY c
            ON c.ID = r.CATEGORY_ID
        WHERE r.USER_ID = #{userId}
    """)
    long countAll(@Param("userId") Long userId);

    /**
     * 로그인한 사용자의 고정 지출을 페이지 단위로 조회한다.
     * 지출일 내림차순으로 정렬하고, 같은 날짜라면 ID가 큰 항목을 먼저 표시한다.
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
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
    """)
    List<RecurringExpenseResponse> findPage(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 조회 월의 납부 대상 개수를 조회한다.
     * 납부 기록이 있으면 현재 활성 상태와 적용 기간에 관계없이 포함한다.
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
     * 조회 월의 전체 대상에 설정된 고정 지출 금액을 합산한다.
     * 납부 완료 항목도 포함하며, 미납 잔액이나 실제 납부 금액 합계는 아니다.
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
     * 조회 월의 납부 대상과 납부 상태를 페이지 단위로 조회한다.
     * 납부 예정일은 해당 월의 말일을 넘지 않도록 보정한다.
     * 보정한 납부 예정일과 고정 지출 ID 순으로 정렬한 뒤 페이징한다.
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
     * 조회한 고정 지출 정보로 실제 생활비를 생성한다.
     * OUTPUT INSERTED.ID로 생성된 생활비 ID를 반환한다.
     * 지출일은 납부 대상 월이 아니라 실제 납부일이다.
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
     * 현재 사용자의 해당 월 납부 기록을 조회한다.
     * */
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
     * 현재 사용자의 해당 월 납부 기록을 삭제한다.
     * OUTPUT DELETED.EXPENSE_ID로 삭제한 기록의 생활비 ID를 반환한다.
     * 대상이 없거나 다른 사용자 소유이면 null을 반환한다.
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
