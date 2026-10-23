package com.myhome.email.signup;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface SignupEmailMapper {

    /**
     * 같은 이메일의 인증번호 발급·확인·가입 확인값 사용을 순서대로 처리한다.
     * 아직 인증 이력이 없는 이메일도 이름 기반 잠금으로 보호한다.
     *
     * 테이블 조회 후 DB 트랜잭션 시작 여부를 확인하고 잠금을 요청한다.
     * 잠금은 트랜잭션 커밋 또는 롤백 시 해제된다.
     * 반드시 서비스의 쓰기 트랜잭션 안에서 호출해야 한다.
     *
     * @param resource 서비스에서 이메일 해시로 구성한 잠금 이름
     * @return 0 이상이면 성공, 음수이면 시간 초과·취소·호출 오류 등의 실패
     */
    @Select("""
    SET NOCOUNT ON;

    -- 결과를 클라이언트로 반환하지 않고 변수에 담는다.
    -- 테이블 조회를 통해 암시적 트랜잭션이 시작되도록 한다.
    DECLARE @probe BIGINT;

    SELECT TOP (1)
        @probe = ID
    FROM dbo.SIGNUP_EMAIL_VERIFICATION;

    -- 서비스의 트랜잭션이 DB 연결에 적용되지 않았다면 명확하게 실패시킨다.
    IF @@TRANCOUNT = 0
    BEGIN
        THROW 50010, N'이메일 인증 잠금에 필요한 DB 트랜잭션이 시작되지 않았습니다.', 1;
    END;

    DECLARE @result INT;

    EXEC @result = sys.sp_getapplock
        @Resource = #{resource},
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 5000;

    SELECT @result AS lockResult;
    """)
    // 잠금은 매번 DB에서 실행해야 하므로 캐시된 조회 결과를 재사용하지 않는다.
    @Options(
            useCache = false,
            flushCache = Options.FlushCachePolicy.TRUE
    )
    int lockEmail(@Param("resource") String resource);

    /**
     * 가장 최근 발급 시각을 조회한다.
     * 재발송 간격 1분을 검사할 때 사용한다.
     */
    @Select("""
        SELECT TOP (1)
            CREATED_DT
        FROM dbo.SIGNUP_EMAIL_VERIFICATION
        WHERE EMAIL = #{email}
        ORDER BY CREATED_DT DESC, ID DESC
        """)
    LocalDateTime findLatestCreated(
            @Param("email") String email
    );

    /**
     * 지정한 시각 이후의 발급 횟수를 조회한다.
     * 발송 실패나 무효화된 요청도 발급 제한에 포함한다.
     */
    @Select("""
        SELECT COUNT(*)
        FROM dbo.SIGNUP_EMAIL_VERIFICATION
        WHERE EMAIL = #{email}
          AND CREATED_DT > #{since}
        """)
    int countRecent(
            @Param("email") String email,
            @Param("since") LocalDateTime since
    );

    /**
     * 재발송 전에 이전 인증 요청을 무효화한다.
     * 이전 인증번호와 가입 확인값 모두 사용할 수 없게 한다.
     * 이미 회원가입에 사용한 이력은 변경하지 않는다.
     */
    @Update("""
        UPDATE dbo.SIGNUP_EMAIL_VERIFICATION
        SET REVOKED_DT = #{now}
        WHERE EMAIL = #{email}
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
        """)
    int revokePrevious(
            @Param("email") String email,
            @Param("now") LocalDateTime now
    );

    /**
     * 새 인증번호의 해시와 유효기간을 저장한다.
     * 인증번호 원문은 DB에 저장하지 않는다.
     */
    @Insert("""
        INSERT INTO dbo.SIGNUP_EMAIL_VERIFICATION (
            REQUEST_ID,
            EMAIL,
            CODE_HASH,
            EXPIRES_DT,
            CREATED_DT
        )
        VALUES (
            #{requestId},
            #{email},
            #{codeHash},
            #{expiresDt},
            #{now}
        )
        """)
    int insert(
            @Param("requestId") String requestId,
            @Param("email") String email,
            @Param("codeHash") String codeHash,
            @Param("expiresDt") LocalDateTime expiresDt,
            @Param("now") LocalDateTime now
    );

    /**
     * 요청 ID와 이메일이 모두 일치하는 인증 이력을 잠금 조회한다.
     * 조회 후 실패 횟수나 인증 완료 상태를 변경할 때 사용한다.
     */
    @Select("""
        SELECT
            ID AS id,
            EMAIL AS email,
            CODE_HASH AS codeHash,
            FAILED_ATTEMPT_COUNT AS failedAttempts,
            EXPIRES_DT AS expiresDt,
            VERIFIED_DT AS verifiedDt,
            USED_DT AS usedDt,
            REVOKED_DT AS revokedDt
        FROM dbo.SIGNUP_EMAIL_VERIFICATION WITH (UPDLOCK, ROWLOCK)
        WHERE REQUEST_ID = #{requestId}
          AND EMAIL = #{email}
        """)
    SignupEmailToken findForUpdate(
            @Param("requestId") String requestId,
            @Param("email") String email
    );

    /**
     * 인증번호가 일치하지 않으면 실패 횟수를 증가시킨다.
     * 서비스에서 이 변경을 커밋한 뒤 오류 응답을 반환해야 한다.
     */
    @Update("""
        UPDATE dbo.SIGNUP_EMAIL_VERIFICATION
        SET FAILED_ATTEMPT_COUNT = FAILED_ATTEMPT_COUNT + 1
        WHERE ID = #{id}
          AND FAILED_ATTEMPT_COUNT < 5
        """)
    int increaseFailures(
            @Param("id") Long id
    );

    /**
     * 인증번호 검증 성공 시 인증 완료 일시와 가입 확인값 해시를 저장한다.
     * 인증 완료는 회원가입 완료가 아니므로 USED_DT는 아직 변경하지 않는다.
     * 실제 회원가입 시 consume()에서 확인값을 사용 완료 처리한다.
     */
    @Update("""
        UPDATE dbo.SIGNUP_EMAIL_VERIFICATION
        SET VERIFIED_DT = #{now},
            PROOF_HASH = #{proofHash},
            PROOF_EXPIRES_DT = #{proofExpiresDt}
        WHERE ID = #{id}
          AND VERIFIED_DT IS NULL
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
          AND EXPIRES_DT > #{now}
          AND FAILED_ATTEMPT_COUNT < 5
        """)
    int markVerified(
            @Param("id") Long id,
            @Param("proofHash") String proofHash,
            @Param("proofExpiresDt") LocalDateTime proofExpiresDt,
            @Param("now") LocalDateTime now
    );

    /**
     * 메일 발송에 실패한 요청을 무효화한다.
     * 해당 요청만 대상으로 하므로 이후 발급한 요청에는 영향을 주지 않는다.
     */
    @Update("""
        UPDATE dbo.SIGNUP_EMAIL_VERIFICATION
        SET REVOKED_DT = #{now}
        WHERE REQUEST_ID = #{requestId}
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
        """)
    int revokeFailedSend(
            @Param("requestId") String requestId,
            @Param("now") LocalDateTime now
    );

    /**
     * 회원가입에 사용할 인증 확인값을 사용 완료 처리한다.
     * 이메일 일치·인증 완료·만료·무효화·재사용 여부를 함께 검사한다.
     *
     * 회원 INSERT와 같은 트랜잭션에서 실행해야 한다.
     * 반환값이 0이면 사용할 수 없는 확인값이다.
     */
    @Update("""
        UPDATE dbo.SIGNUP_EMAIL_VERIFICATION
        SET USED_DT = #{now}
        WHERE EMAIL = #{email}
          AND PROOF_HASH = #{proofHash}
          AND VERIFIED_DT IS NOT NULL
          AND PROOF_EXPIRES_DT > #{now}
          AND USED_DT IS NULL
          AND REVOKED_DT IS NULL
        """)
    int consume(
            @Param("email") String email,
            @Param("proofHash") String proofHash,
            @Param("now") LocalDateTime now
    );

    /**
     * 실제 인증 완료 시각을 조회한다.
     * consume 성공 후 USER.EMAIL_VERIFIED_DT에 저장할 값이다.
     */
    @Select("""
        SELECT VERIFIED_DT
        FROM dbo.SIGNUP_EMAIL_VERIFICATION
        WHERE EMAIL = #{email}
          AND PROOF_HASH = #{proofHash}
        """)
    LocalDateTime findVerifiedDt(
            @Param("email") String email,
            @Param("proofHash") String proofHash
    );
}