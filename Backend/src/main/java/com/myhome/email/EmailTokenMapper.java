package com.myhome.email;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 이메일 인증에 필요한 회원 정보와 토큰을 조회·변경한다.
 * 현재 메서드는 EMAIL_VERIFY 목적의 토큰만 처리한다.
 * 잠금 조회는 서비스 트랜잭션 안에서 회원 → 토큰 순서로 호출한다.
 */
@Mapper
public interface EmailTokenMapper {

    /**
     * 인증 대상 회원 정보를 조회하고 변경용 잠금을 유지한다.
     * 같은 회원의 동시 발송·인증 요청을 순서대로 처리하기 위해 사용한다.
     * 반드시 서비스의 쓰기 트랜잭션 안에서 호출한다.
     */
    @Select("""
        SELECT
            ID AS userId,
            EMAIL AS email,
            STATUS AS status,
            EMAIL_VERIFIED_DT AS emailVerifiedDt
        FROM dbo.[USER] WITH (UPDLOCK, HOLDLOCK)
        WHERE ID = #{userId}
    """)
    EmailVerificationUser findVerificationUser(@Param("userId") Long userId);


    /**
     * 이메일 인증번호의 최근 발급 시각을 조회한다.
     * 사용·폐기 여부와 관계없이 조회하여 재발송 제한에 사용한다.
     * 발급 이력이 없으면 null을 반환한다.
     */
    @Select("""
        SELECT MAX(CREATED_DT)
        FROM dbo.USER_EMAIL_TOKEN
        WHERE USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
    """)
    LocalDateTime findLatestIssuedAt(@Param("userId") Long userId);

    /**
     * 지정한 시각 이후 발급한 이메일 인증번호 건수를 조회한다.
     * 폐기되거나 사용된 토큰도 포함하여 발송 제한 우회를 방지한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.USER_EMAIL_TOKEN
        WHERE USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
          AND CREATED_DT >= #{sinceUtc,jdbcType=TIMESTAMP}
        """)
    long countRecentIssues(
            @Param("userId") Long userId,
            @Param("sinceUtc") LocalDateTime sinceUtc
    );

    /**
     * 같은 회원의 기존 미사용 이메일 인증번호를 무효화한다.
     * 이메일 변경·비밀번호 재설정 토큰은 변경하지 않는다.
     */
    @Update("""
        UPDATE dbo.USER_EMAIL_TOKEN
        SET REVOKED_DT = #{nowUtc,jdbcType=TIMESTAMP}
        WHERE USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
        """)
    int revokeUnusedTokens(
            @Param("userId") Long userId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    /**
     * 새 이메일 인증번호의 HMAC 해시와 만료 정보를 저장한다.
     * 원본 인증번호는 DB에 저장하지 않는다.
     * 요청 ID는 서비스에서 UUID로 생성한 뒤 문자열로 전달한다.
     */
    @Insert("""
        INSERT INTO dbo.USER_EMAIL_TOKEN (
            REQUEST_ID,
            USER_ID,
            PURPOSE,
            TARGET_EMAIL,
            TOKEN_HASH,
            FAILED_ATTEMPT_COUNT,
            EXPIRES_DT,
            USED_DT,
            REVOKED_DT,
            CREATED_DT
        )
        VALUES (
            CONVERT(UNIQUEIDENTIFIER, #{requestId,jdbcType=VARCHAR}),
            #{userId},
            'EMAIL_VERIFY',
            #{targetEmail,jdbcType=NVARCHAR},
            #{tokenHash,jdbcType=CHAR},
            0,
            #{expiresUtc,jdbcType=TIMESTAMP},
            NULL,
            NULL,
            #{nowUtc,jdbcType=TIMESTAMP}
        )
        """)
    int insertToken(
            @Param("requestId") String requestId,
            @Param("userId") Long userId,
            @Param("targetEmail") String targetEmail,
            @Param("tokenHash") String tokenHash,
            @Param("expiresUtc") LocalDateTime expiresUtc,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    /**
     * 본인의 이메일 인증 토큰을 변경용 잠금으로 조회한다.
     * 만료·사용·폐기 여부는 서비스에서 구분해 검사한다.
     * 회원 행을 먼저 잠근 다음 이 메서드를 호출한다.
     */
    @Select("""
        SELECT
            ID AS id,
            CONVERT(VARCHAR(36), REQUEST_ID) AS requestId,
            USER_ID AS userId,
            PURPOSE AS purpose,
            TARGET_EMAIL AS targetEmail,
            TOKEN_HASH AS tokenHash,
            FAILED_ATTEMPT_COUNT AS failedAttemptCount,
            EXPIRES_DT AS expiresDt,
            USED_DT AS usedDt,
            REVOKED_DT AS revokedDt,
            CREATED_DT AS createdDt
        FROM dbo.USER_EMAIL_TOKEN WITH (UPDLOCK, HOLDLOCK)
        WHERE REQUEST_ID =
              CONVERT(UNIQUEIDENTIFIER, #{requestId,jdbcType=VARCHAR})
          AND USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
        """)
    EmailToken findTokenForUpdate(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    /**
     * 유효한 인증번호의 입력 실패 횟수를 증가시킨다.
     * 이번 실패로 최대 횟수에 도달하면 동시에 무효화한다.
     * 이미 사용·폐기·만료된 토큰은 수정하지 않는다.
     */
    @Update("""
        UPDATE dbo.USER_EMAIL_TOKEN
        SET FAILED_ATTEMPT_COUNT = FAILED_ATTEMPT_COUNT + 1,
            REVOKED_DT = CASE
                WHEN FAILED_ATTEMPT_COUNT + 1 >= #{maxAttempts}
                    THEN #{nowUtc,jdbcType=TIMESTAMP}
                ELSE REVOKED_DT
            END
        WHERE ID = #{id}
          AND USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
          AND EXPIRES_DT > #{nowUtc,jdbcType=TIMESTAMP}
          AND FAILED_ATTEMPT_COUNT < #{maxAttempts}
        """)
    int increaseFailedAttempt(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("maxAttempts") int maxAttempts,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    /**
     * 인증에 성공한 토큰을 사용 완료 상태로 변경한다.
     * 유효한 미사용 토큰만 변경하여 재사용을 방지한다.
     */
    @Update("""
        UPDATE dbo.USER_EMAIL_TOKEN
        SET USED_DT = #{nowUtc,jdbcType=TIMESTAMP}
        WHERE ID = #{id}
          AND USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
          AND EXPIRES_DT > #{nowUtc,jdbcType=TIMESTAMP}
          AND FAILED_ATTEMPT_COUNT < #{maxAttempts}
        """)
    int markUsed(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("maxAttempts") int maxAttempts,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    /**
     * 활성 회원의 현재 이메일을 인증 완료 상태로 변경한다.
     * 토큰의 대상 이메일이 현재 이메일과 같은 경우에만 반영한다.
     * 알림 수신 동의 여부는 변경하지 않는다.
     */
    @Update("""
        UPDATE dbo.[USER]
        SET EMAIL_VERIFIED_DT = #{nowUtc,jdbcType=TIMESTAMP},
            UPDATED_DT = #{nowUtc,jdbcType=TIMESTAMP}
        WHERE ID = #{userId}
          AND STATUS = 'ACTIVE'
          AND EMAIL = #{targetEmail,jdbcType=NVARCHAR}
          AND EMAIL_VERIFIED_DT IS NULL
        """)
    int markEmailVerified(
            @Param("userId") Long userId,
            @Param("targetEmail") String targetEmail,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    /**
     * 메일 발송 실패 등으로 특정 발급 건만 무효화한다.
     * 그 사이 새로 발급된 다른 인증번호에는 영향을 주지 않는다.
     */
    @Update("""
        UPDATE dbo.USER_EMAIL_TOKEN
        SET REVOKED_DT = #{nowUtc,jdbcType=TIMESTAMP}
        WHERE REQUEST_ID =
              CONVERT(UNIQUEIDENTIFIER, #{requestId,jdbcType=VARCHAR})
          AND USER_ID = #{userId}
          AND PURPOSE = 'EMAIL_VERIFY'
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
        """)
    int revokeToken(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

}
