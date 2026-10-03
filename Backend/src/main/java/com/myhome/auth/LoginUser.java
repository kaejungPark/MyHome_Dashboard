package com.myhome.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Spring Security에서 사용하는 인증용 사용자 정보다.
 * API 응답에는 직접 반환하지 않는다.
 */
public class LoginUser extends User {

    private static final long serialVersionUID = 1L;

    // 로그인한 사용자의 데이터 조회에 사용할 ID다.
    private final Long id;

    // 화면에 표시할 닉네임이다.
    private final String nickname;

    public LoginUser(AuthUser user) {
        super(
                user.email(),          // 로그인 식별자로 이메일을 사용한다.
                user.passwordHash(),   // 비밀번호 비교에 사용할 저장된 해시다.
                "ACTIVE".equals(user.status()), // 활성 계정만 인증을 허용한다.
                true,                  // 계정 만료 정책은 아직 적용하지 않는다.
                true,                  // 비밀번호 만료 정책은 아직 적용하지 않는다.
                true,                  // 별도의 잠금 정책은 아직 적용하지 않는다.
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
        );

        this.id = user.id();
        this.nickname = user.nickname();
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }
}