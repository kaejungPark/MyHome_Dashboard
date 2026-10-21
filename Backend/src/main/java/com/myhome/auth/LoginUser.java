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

    // 로그인한 사용자의 권한
    private final String role;

    public LoginUser(AuthUser user) {
        super(
                user.email(),          // 로그인 식별자로 이메일을 사용한다.
                user.passwordHash(),   // 비밀번호 비교에 사용할 저장된 해시다.
                "ACTIVE".equals(user.status()), // 활성 계정만 인증을 허용한다.
                true,                  // 계정 만료 정책은 아직 적용하지 않는다.
                true,                  // 비밀번호 만료 정책은 아직 적용하지 않는다.
                true,                  // 별도의 잠금 정책은 아직 적용하지 않는다.
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role())) // DB의 USER·ADMIN 값을 Security가 검사할 ROLE_USER·ROLE_ADMIN 권한으로 변환한다.
        );

        this.id = user.id();
        this.nickname = user.nickname();
        this.role = user.role();
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() { return role; }

    /**
     * 기존 인증 정보와 권한은 유지하면서 닉네임만 변경한다.
     * 세션 갱신을 위해 비밀번호 해시를 다시 저장하지 않는다.
     */
    private LoginUser(LoginUser source, String nickname) {
        super(
                source.getUsername(),
                "",
                source.isEnabled(),
                source.isAccountNonExpired(),
                source.isCredentialsNonExpired(),
                source.isAccountNonLocked(),
                source.getAuthorities()
        );

        this.id = source.id;
        this.nickname = nickname;
        this.role = source.role;

        eraseCredentials();
    }

    /**
     * 변경된 닉네임을 가진 새 로그인 사용자 정보를 반환한다.
     */
    public LoginUser withNickname(String nickname) {
        return new LoginUser(this, nickname);
    }
}