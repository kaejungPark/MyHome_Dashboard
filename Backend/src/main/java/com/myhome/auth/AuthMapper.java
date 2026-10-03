package com.myhome.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
     * 비밀번호 해시와 사용자 정보를 저장한다.
     * 신규 회원의 권한은 USER, 계정 상태는 ACTIVE로 지정한다.
     */
    @Insert("""
        INSERT INTO dbo.[USER] (
            EMAIL,
            PASSWORD_HASH,
            NICKNAME,
            ROLE,
            STATUS,
            CREATED_DT,
            UPDATED_DT
        )
        VALUES (
            #{email},
            #{passwordHash},
            #{nickname},
            'USER',
            'ACTIVE',
            SYSUTCDATETIME(),
            SYSUTCDATETIME()
        )
        """)
    int insert(
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("nickname") String nickname
    );
}