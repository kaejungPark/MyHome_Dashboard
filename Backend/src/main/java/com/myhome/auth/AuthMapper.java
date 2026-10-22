package com.myhome.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface AuthMapper {

    /**
     * 동일한 이메일이 존재하면 true, 없으면 false를 반환한다.
     */
    @Select("""
        SELECT CAST(
            CASE WHEN EXISTS (
                SELECT 1
                FROM dbo.[USER]
                WHERE EMAIL = #{email}
            ) THEN 1 ELSE 0 END
            AS BIT
        )
        """)
    boolean existsByEmail(@Param("email") String email);

    /**
     * 이메일로 로그인 검증에 필요한 사용자 정보를 조회한다.
     * 일치하는 사용자가 없으면 null을 반환한다.
     */
    @Select("""
    SELECT
        ID AS id,
        EMAIL AS email,
        PASSWORD_HASH AS passwordHash,
        NICKNAME AS nickname,
        ROLE AS role,
        STATUS AS status
    FROM dbo.[USER]
    WHERE EMAIL = #{email}
    """)
    AuthUser findByEmail(@Param("email") String email);

    /**
     * 로그인에 성공한 사용자의 마지막 로그인 시간을 UTC 기준으로 갱신한다.
     */
    @Update("""
        UPDATE dbo.[USER]
        SET LAST_LOGIN_DT = SYSUTCDATETIME()
        WHERE ID = #{userId}
    """)
    int updateLastLoginDt(@Param("userId") Long userId);

    /**
     * 본인의 회원 정보와 현재 이메일 인증 상태를 조회한다.
     */
    @Select("""
    SELECT
        EMAIL AS email,
        NAME AS name,
        NICKNAME AS nickname,
        AGE AS age,
        GENDER AS gender,
        CAST(
            CASE
                WHEN EMAIL_VERIFIED_DT IS NOT NULL THEN 1
                ELSE 0
            END
            AS BIT
        ) AS emailVerified,
        EMAIL_NOTIFICATION_ENABLED AS emailNotificationEnabled
    FROM dbo.[USER]
    WHERE ID = #{userId}
    """)
    ProfileResponse findProfile(@Param("userId") Long userId);

    /**
     * 회원 정보 수정과 비밀번호 검증에 필요한 사용자 정보를 조회한다.
     * 비밀번호 해시는 서버 내부에서만 사용한다.
     */
    @Select("""
    SELECT
        ID AS id,
        EMAIL AS email,
        PASSWORD_HASH AS passwordHash,
        NICKNAME AS nickname,
        ROLE AS role,
        STATUS AS status
    FROM dbo.[USER]
    WHERE ID = #{userId}
    """)
    AuthUser findById(@Param("userId") Long userId);

    /**
     * 활성 계정의 일반 회원 정보와 이메일 알림 수신 동의를 수정한다.
     * 이메일 주소·인증 상태·비밀번호·권한·계정 상태는 변경하지 않는다.
     */
    @Update("""
    UPDATE dbo.[USER]
    SET NAME = #{name,jdbcType=NVARCHAR},
        NICKNAME = #{nickname,jdbcType=NVARCHAR},
        AGE = #{age,jdbcType=INTEGER},
        GENDER = #{gender,jdbcType=VARCHAR},
        EMAIL_NOTIFICATION_ENABLED = #{emailNotificationEnabled,jdbcType=BIT},
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{userId}
      AND STATUS = 'ACTIVE'
    """)
    int updateProfile(
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("nickname") String nickname,
            @Param("age") Integer age,
            @Param("gender") String gender,
            @Param("emailNotificationEnabled") boolean emailNotificationEnabled
    );

    /**
     * 검증에 사용한 비밀번호 해시가 그대로인 경우에만 변경한다.
     * 동시에 들어온 다른 변경 요청이 먼저 처리됐다면 0을 반환한다.
     */
    @Update("""
    UPDATE dbo.[USER]
    SET PASSWORD_HASH = #{newPasswordHash},
        UPDATED_DT = SYSUTCDATETIME()
    WHERE ID = #{userId}
      AND STATUS = 'ACTIVE'
      AND PASSWORD_HASH COLLATE Latin1_General_100_BIN2
          = #{oldPasswordHash}
    """)
    int updatePassword(
            @Param("userId") Long userId,
            @Param("oldPasswordHash") String oldPasswordHash,
            @Param("newPasswordHash") String newPasswordHash
    );

    /**
     * 이메일 인증을 완료한 회원의 가입 정보를 저장한다.
     * 인증 완료 시각은 서버에서 검증한 이력의 값을 사용한다.
     * 권한과 계정 상태는 서버에서 USER·ACTIVE로 지정한다.
     */
    @Insert("""
    INSERT INTO dbo.[USER] (
        EMAIL,
        PASSWORD_HASH,
        NAME,
        NICKNAME,
        AGE,
        GENDER,
        ROLE,
        STATUS,
        EMAIL_VERIFIED_DT,
        EMAIL_NOTIFICATION_ENABLED,
        CREATED_DT,
        UPDATED_DT
    )
    VALUES (
        #{email},
        #{passwordHash},
        #{name,jdbcType=NVARCHAR},
        #{nickname},
        #{age,jdbcType=INTEGER},
        #{gender,jdbcType=VARCHAR},
        'USER',
        'ACTIVE',
        #{emailVerifiedDt,jdbcType=TIMESTAMP},
        #{emailNotificationEnabled,jdbcType=BIT},
        SYSUTCDATETIME(),
        SYSUTCDATETIME()
    )
    """)
    int insert(
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("name") String name,
            @Param("nickname") String nickname,
            @Param("age") Integer age,
            @Param("gender") String gender,
            @Param("emailVerifiedDt") LocalDateTime emailVerifiedDt,
            @Param("emailNotificationEnabled") boolean emailNotificationEnabled
    );
}