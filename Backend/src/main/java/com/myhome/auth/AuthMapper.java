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
     * 회원가입 정보를 저장한다.
     * 나이와 성별은 미입력 시 NULL로 저장한다.
     * 권한과 계정 상태는 서버에서 USER와 ACTIVE로 지정한다.
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
            @Param("gender") String gender
    );
}