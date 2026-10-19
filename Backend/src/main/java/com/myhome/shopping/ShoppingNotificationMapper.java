package com.myhome.shopping;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ShoppingNotificationMapper {

    /**
     * 활성 회원의 미구매 항목 중 구매 예정일이 오늘 또는 내일인 항목을 조회한다.
     * 사용자별로 묶어 발송할 수 있도록 수신 이메일과 물품 정보를 함께 반환한다.
     */
    @Select("""
       SELECT
        s.USER_ID AS userId,
        u.EMAIL AS email,
        s.ID AS shoppingItemId,
        s.NAME AS name,
        s.QUANTITY AS quantity,
        s.UNIT AS unit,
        s.PURCHASE_DUE_DATE AS purchaseDueDate
       FROM dbo.SHOPPING_ITEM s
       JOIN dbo.[USER] u ON s.USER_ID = u.ID
       WHERE s.IS_COMPLETED = 0
        AND s.PURCHASE_DUE_DATE IN (#{today}, #{tomorrow})
        AND u.STATUS = 'ACTIVE'
       ORDER BY s.USER_ID ASC, s.PURCHASE_DUE_DATE ASC, s.ID ASC
    """)
    List<ShoppingNotificationTarget> findTargets(
            @Param("today") LocalDate today,
            @Param("tomorrow")LocalDate tomorrow
    );

    /**
     * 사용자의 해당 날짜 발송 이력을 조회한다.
     * 이력이 없으면 null을 반환한다.
     */
    @Select("""
    SELECT
        ID AS id,
        STATUS AS status,
        ATTEMPT_COUNT AS attemptCount,
        UPDATED_DT AS updatedDt
    FROM dbo.SHOPPING_NOTIFICATION_HISTORY
    WHERE USER_ID = #{userId}
      AND NOTIFICATION_DATE = #{notificationDate,jdbcType=DATE}
    """)
    ShoppingNotificationHistory findHistory(
            @Param("userId") Long userId,
            @Param("notificationDate") LocalDate notificationDate
    );

    /**
     * 최초 발송을 준비하는 이력을 생성한다.
     * 동일 사용자·날짜의 중복 생성은 DB UNIQUE 제약으로 차단한다.
     */
    @Insert("""
    INSERT INTO dbo.SHOPPING_NOTIFICATION_HISTORY (
        USER_ID,
        NOTIFICATION_DATE,
        RECIPIENT_EMAIL,
        STATUS,
        ATTEMPT_COUNT,
        CREATED_DT,
        UPDATED_DT
    )
    VALUES (
        #{userId},
        #{notificationDate,jdbcType=DATE},
        #{recipientEmail},
        'PROCESSING',
        1,
        SYSUTCDATETIME(),
        SYSUTCDATETIME()
    )
    """)
    int insertProcessing(
            @Param("userId") Long userId,
            @Param("notificationDate") LocalDate notificationDate,
            @Param("recipientEmail") String recipientEmail
    );

    /**
     * 조회했던 실패 이력을 재시도 상태로 변경한다.
     * 상태와 시도 횟수가 그대로인 경우에만 갱신하여 동시 재시도를 막는다.
     * 재시도 가능 시간과 최대 횟수는 Service에서 별도로 검증한다.
     */
    @Update("""
    UPDATE dbo.SHOPPING_NOTIFICATION_HISTORY
    SET STATUS = 'PROCESSING',
        RECIPIENT_EMAIL = #{recipientEmail},
        ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
        SENT_DT = NULL,
        ERROR_MESSAGE = NULL,
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{id}
      AND STATUS = 'FAILED'
      AND ATTEMPT_COUNT = #{expectedAttemptCount}
    """)
    int retryFailed(
            @Param("id") Long id,
            @Param("recipientEmail") String recipientEmail,
            @Param("expectedAttemptCount") int expectedAttemptCount
    );

    /**
     * 현재 발송 시도의 성공 결과를 저장한다.
     * 메일 서버로의 전송 성공을 의미하며 실제 수신이나 열람을 보장하지 않는다.
     */
    @Update("""
    UPDATE dbo.SHOPPING_NOTIFICATION_HISTORY
    SET STATUS = 'SENT',
        SENT_DT = SYSUTCDATETIME(),
        ERROR_MESSAGE = NULL,
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{id}
      AND STATUS = 'PROCESSING'
      AND ATTEMPT_COUNT = #{expectedAttemptCount}
    """)
    int markSent(
            @Param("id") Long id,
            @Param("expectedAttemptCount") int expectedAttemptCount
    );

    /**
     * 현재 발송 시도의 실패 결과를 저장한다.
     * 오류 메시지는 DB 컬럼 길이를 초과하지 않도록 최대 1000자로 제한한다.
     */
    @Update("""
    UPDATE dbo.SHOPPING_NOTIFICATION_HISTORY
    SET STATUS = 'FAILED',
        SENT_DT = NULL,
        ERROR_MESSAGE = LEFT(#{errorMessage,jdbcType=NVARCHAR}, 1000),
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{id}
      AND STATUS = 'PROCESSING'
      AND ATTEMPT_COUNT = #{expectedAttemptCount}
    """)
    int markFailed(
            @Param("id") Long id,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("errorMessage") String errorMessage
    );
}
