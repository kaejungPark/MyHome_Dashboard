package com.myhome.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Spring Security에서 사용하는 인증용 사용자 정보다.
 *
 * 로그인에 성공하면 세션의 인증 정보에 포함된다.
 * 이 객체 자체를 API 응답으로 반환하지 않는다.
 *
 * 일반 로그인과 임시 비밀번호 로그인을 구분하며,
 * 임시 로그인에는 비밀번호 변경 전용 권한만 부여한다.
 */
public class LoginUser extends User {

    private static final long serialVersionUID = 1L;

    // 로그인한 사용자의 데이터 조회에 사용할 ID다.
    private final Long id;

    // 화면에 표시할 닉네임이다.
    private final String nickname;

    // 일반 로그인은 USER·ADMIN, 임시 로그인은 PASSWORD_RESET이다.
    private final String role;

    // 로그인 당시의 인증 버전이다. DB 버전이 달라지면 세션을 거절한다.
    private final int authVersion;

    // 임시 로그인에 사용한 발급 요청 ID다. 일반 로그인에서는 null이다.
    private final String passwordResetRequestId;

    /**
     * 기존 일반 로그인 과정에서 호출하는 생성자다.
     * AuthUserDetailsService에서 조회한 회원 정보를 전달한다.
     */
    public LoginUser(AuthUser user) {
        this(user, null);
    }

    /**
     * 로그인 종류에 맞게 비밀번호 비교값과 권한을 구성한다.
     *
     * 임시 로그인은 서비스에서 이미 비밀번호를 검증했으므로
     * 일반 비밀번호 해시를 세션용 객체에 넣지 않는다.
     */
    private LoginUser(AuthUser user, String resetRequestId) {
        super(
                user.email(), // 로그인 식별자로 사용하는 이메일

                // 일반 인증에서만 BCrypt 비교를 위한 해시가 필요하다.
                resetRequestId == null ? user.passwordHash() : "",

                "ACTIVE".equals(user.status()), // 활성 회원만 인증 가능
                true, // 별도의 계정 만료 정책은 적용하지 않는다.
                true, // 임시 비밀번호 만료는 서비스와 필터에서 검사한다.
                true, // 계정 전체 잠금 정책과 임시 비밀번호 실패 제한은 별개다.

                // 임시 로그인에는 기존 USER·ADMIN 권한을 부여하지 않는다.
                List.of(new SimpleGrantedAuthority(
                        resetRequestId == null
                                ? "ROLE_" + user.role()
                                : "ROLE_PASSWORD_RESET"
                ))
        );

        this.id = user.id();
        this.nickname = user.nickname();
        this.role = resetRequestId == null
                ? user.role()
                : "PASSWORD_RESET";
        this.authVersion = user.authVersion();
        this.passwordResetRequestId = resetRequestId;
    }

    /**
     * 임시 비밀번호 검증에 성공한 경우에만 호출한다.
     *
     * 발급 요청 ID를 세션에 보관하여 이후 재발급·만료·사용 완료를
     * 서버 필터에서 계속 확인할 수 있도록 한다.
     */
    public static LoginUser forPasswordReset(
            AuthUser user,
            String requestId
    ) {
        return new LoginUser(user, requestId);
    }

    /**
     * 기존 인증 정보와 권한을 유지하면서 닉네임만 변경한다.
     *
     * 인증 버전과 임시 로그인 요청 ID도 보존한다.
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
        this.authVersion = source.authVersion;
        this.passwordResetRequestId = source.passwordResetRequestId;

        eraseCredentials();
    }

    /** 변경된 닉네임을 가진 새 로그인 사용자 정보를 반환한다. */
    public LoginUser withNickname(String nickname) {
        return new LoginUser(this, nickname);
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public String getPasswordResetRequestId() {
        return passwordResetRequestId;
    }

    /**
     * 임시 비밀번호로 로그인했는지 반환한다.
     * true이면 새 비밀번호로 변경하기 전까지 일반 기능 이용을 제한한다.
     */
    public boolean isPasswordChangeRequired() {
        return passwordResetRequestId != null;
    }
}