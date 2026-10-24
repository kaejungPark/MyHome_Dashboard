package com.myhome.auth.password;

import com.myhome.auth.AuthUser;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

/**
 * 임시 비밀번호 발급 이력과 검증 상태를 관리한다.
 *
 * 일반 비밀번호는 USER.PASSWORD_HASH에 유지하고,
 * 임시 비밀번호 해시는 PASSWORD_RESET 테이블에 별도로 저장한다.
 *
 * 시간 컬럼은 모두 UTC 기준으로 저장하고 비교한다.
 */
@Mapper
public interface PasswordResetMapper {

    /**
     * 임시 비밀번호 검증에 필요한 서버 내부 이력이다.
     *
     * 비밀번호 해시가 포함되어 있으므로 API 응답에 사용하지 않는다.
     * record 전체를 로그로 출력하지 않는다.
     */
    record Ticket(
            String requestId,       // 임시 비밀번호 발급 요청 ID
            Long userId,            // 발급 대상 회원 ID
            String passwordHash,    // 임시 비밀번호의 BCrypt 해시
            int authVersion,        // 발급 당시 회원의 인증 버전
            LocalDateTime expiresAt,// 임시 비밀번호 만료 시각(UTC)
            int failedAttempts,     // 임시 비밀번호 검증 실패 횟수

            /*
             * SENDING: 발송 준비
             * SENT: SMTP 발송 요청 성공
             * FAILED: 메일 발송 실패
             * REVOKED: 재발급 등으로 무효화
             * USED: 새 비밀번호 변경에 사용 완료
             */
            String status
    ) {
    }

    /**
     * 이메일 인증을 완료한 활성 회원의 ID를 조회한다.
     *
     * 존재하지 않는 이메일, 인증하지 않은 계정,
     * 정지·탈퇴 계정은 발급 대상으로 선택하지 않는다.
     *
     * @return 발급 대상 회원 ID, 대상이 아니면 null
     */
    @Select("""
        SELECT ID
        FROM dbo.[USER]
        WHERE EMAIL = #{email}
          AND STATUS = 'ACTIVE'
          AND EMAIL_VERIFIED_DT IS NOT NULL
        """)
    Long findEligibleUserId(@Param("email") String email);

    /**
     * 회원 행에 갱신 잠금을 걸고 현재 회원 정보를 조회한다.
     *
     * 같은 회원에 대한 발급·검증·변경 요청이 동시에 들어와도
     * 발급 제한과 실패 횟수 처리가 순서대로 실행되게 한다.
     *
     * 잠금은 트랜잭션 종료 시 해제되므로
     * 반드시 서비스의 쓰기 트랜잭션 안에서 호출해야 한다.
     *
     * 잠금 조회는 매번 DB에서 실행되도록 MyBatis 캐시를 사용하지 않는다.
     */
    @Select("""
        SELECT
            ID AS id,
            EMAIL AS email,
            PASSWORD_HASH AS passwordHash,
            NICKNAME AS nickname,
            ROLE AS role,
            STATUS AS status,
            AUTH_VERSION AS authVersion
        FROM dbo.[USER] WITH (UPDLOCK, ROWLOCK)
        WHERE ID = #{userId}
        """)
    @Options(
            useCache = false,
            flushCache = Options.FlushCachePolicy.TRUE
    )
    AuthUser lockUser(@Param("userId") Long userId);

    /**
     * 가장 최근 발급 요청 시각을 조회한다.
     *
     * 최소 재발송 간격 1분을 검사할 때 사용한다.
     * 발송에 실패한 요청도 반복 발송 제한에 포함한다.
     */
    @Select("""
        SELECT MAX(CREATED_DT)
        FROM dbo.PASSWORD_RESET
        WHERE USER_ID = #{userId}
        """)
    LocalDateTime findLastCreated(@Param("userId") Long userId);

    /**
     * 지정한 시각 이후 생성된 발급 요청 수를 조회한다.
     *
     * 서비스에서 현재 시각의 1시간 전을 전달하여
     * 시간당 최대 5회 제한을 검사한다.
     */
    @Select("""
        SELECT COUNT(*)
        FROM dbo.PASSWORD_RESET
        WHERE USER_ID = #{userId}
          AND CREATED_DT > #{since}
        """)
    int countRecent(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    /**
     * 새 임시 비밀번호 발급 전에 이전 요청을 무효화한다.
     *
     * 발송 중인 요청도 무효화하여 늦게 완료된 이전 메일이
     * 다시 사용 가능한 상태로 바뀌지 않도록 한다.
     *
     * 이전 요청으로 만들어진 임시 로그인 세션도
     * 이후 필터 검사에서 거절된다.
     */
    @Update("""
        UPDATE dbo.PASSWORD_RESET
        SET STATUS = 'REVOKED'
        WHERE USER_ID = #{userId}
          AND STATUS IN ('SENDING', 'SENT')
        """)
    int revokePrevious(@Param("userId") Long userId);

    /**
     * 메일을 보내기 전에 발급 이력을 SENDING 상태로 저장한다.
     *
     * 임시 비밀번호 원문은 저장하지 않는다.
     * 발급 당시 인증 버전을 함께 저장하여 실제 비밀번호가 변경되면
     * 이전에 발급한 임시 비밀번호도 사용할 수 없게 한다.
     */
    @Insert("""
        INSERT INTO dbo.PASSWORD_RESET (
            REQUEST_ID,
            USER_ID,
            PASSWORD_HASH,
            AUTH_VERSION,
            STATUS,
            FAILED_ATTEMPTS,
            EXPIRES_DT,
            CREATED_DT
        )
        VALUES (
            #{requestId},
            #{userId},
            #{passwordHash},
            #{authVersion},
            'SENDING',
            0,
            #{expiresAt},
            #{now}
        )
        """)
    int insert(
            @Param("requestId") String requestId,
            @Param("userId") Long userId,
            @Param("passwordHash") String passwordHash,
            @Param("authVersion") int authVersion,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now
    );

    /**
     * SMTP 발송 요청 성공 후 임시 비밀번호를 활성화한다.
     *
     * 실제 사용 가능한 시간은 활성화 시점부터 15분으로 설정한다.
     * 발송 중 재발급되거나 회원 비밀번호가 변경된 요청은
     * 조건을 만족하지 않으므로 활성화하지 않는다.
     *
     * SENT는 수신함 도착을 보장하는 상태가 아니다.
     *
     * @return 활성화 시 1, 이미 무효화되는 등 조건 불일치 시 0
     */
    @Update("""
        UPDATE r
        SET STATUS = 'SENT',
            EXPIRES_DT = DATEADD(MINUTE, 15, SYSUTCDATETIME())
        FROM dbo.PASSWORD_RESET r
        INNER JOIN dbo.[USER] u ON u.ID = r.USER_ID
        WHERE r.REQUEST_ID = #{requestId}
          AND r.STATUS = 'SENDING'
          AND u.STATUS = 'ACTIVE'
          AND u.AUTH_VERSION = r.AUTH_VERSION
        """)
    int activate(@Param("requestId") String requestId);

    /**
     * 메일 발송에 실패한 현재 요청만 FAILED 상태로 변경한다.
     *
     * 요청 ID와 SENDING 조건을 함께 사용하므로
     * 다른 재발급 요청이나 이미 무효화된 상태는 덮어쓰지 않는다.
     */
    @Update("""
        UPDATE dbo.PASSWORD_RESET
        SET STATUS = 'FAILED'
        WHERE REQUEST_ID = #{requestId}
          AND STATUS = 'SENDING'
        """)
    int markFailed(@Param("requestId") String requestId);

    /**
     * 회원의 발송 완료 상태 임시 비밀번호를 조회한다.
     *
     * 사용자별 SENT 상태는 DB 고유 인덱스로 최대 1건만 허용한다.
     * 만료·실패 횟수·인증 버전은 서비스에서 추가 검증한다.
     */
    @Select("""
        SELECT
            REQUEST_ID AS requestId,
            USER_ID AS userId,
            PASSWORD_HASH AS passwordHash,
            AUTH_VERSION AS authVersion,
            EXPIRES_DT AS expiresAt,
            FAILED_ATTEMPTS AS failedAttempts,
            STATUS AS status
        FROM dbo.PASSWORD_RESET
        WHERE USER_ID = #{userId}
          AND STATUS = 'SENT'
        """)
    Ticket findSent(@Param("userId") Long userId);

    /**
     * 임시 로그인 세션이 참조하는 특정 발급 이력을 조회한다.
     *
     * 최신 이력을 임의로 사용하는 것이 아니라,
     * 로그인할 때 검증했던 요청 ID가 여전히 유효한지 확인한다.
     */
    @Select("""
        SELECT
            REQUEST_ID AS requestId,
            USER_ID AS userId,
            PASSWORD_HASH AS passwordHash,
            AUTH_VERSION AS authVersion,
            EXPIRES_DT AS expiresAt,
            FAILED_ATTEMPTS AS failedAttempts,
            STATUS AS status
        FROM dbo.PASSWORD_RESET
        WHERE REQUEST_ID = #{requestId}
        """)
    Ticket findByRequestId(@Param("requestId") String requestId);

    /**
     * 임시 비밀번호가 일치하지 않을 때 실패 횟수를 1 증가시킨다.
     *
     * 최대 5회까지만 증가하며, 5회에 도달한 요청은
     * 서비스의 유효성 검사에서 사용할 수 없는 요청으로 판단한다.
     */
    @Update("""
        UPDATE dbo.PASSWORD_RESET
        SET FAILED_ATTEMPTS = FAILED_ATTEMPTS + 1
        WHERE REQUEST_ID = #{requestId}
          AND STATUS = 'SENT'
          AND FAILED_ATTEMPTS < 5
        """)
    int incrementFailure(@Param("requestId") String requestId);

    /**
     * 새 비밀번호 저장에 사용한 임시 비밀번호를 사용 완료 처리한다.
     *
     * 실제 비밀번호 갱신과 같은 트랜잭션에서 실행해야 한다.
     * 사용 완료된 임시 비밀번호는 다시 로그인에 사용할 수 없다.
     */
    @Update("""
        UPDATE dbo.PASSWORD_RESET
        SET STATUS = 'USED'
        WHERE REQUEST_ID = #{requestId}
          AND STATUS = 'SENT'
        """)
    int markUsed(@Param("requestId") String requestId);
}