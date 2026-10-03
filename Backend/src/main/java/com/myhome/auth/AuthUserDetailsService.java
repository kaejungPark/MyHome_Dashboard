package com.myhome.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 사용자 정보를 Spring Security 인증 과정에 제공한다.
 */
@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthMapper authMapper;

    public AuthUserDetailsService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    /**
     * 로그인에 사용할 사용자 정보를 이메일로 조회한다.
     *
     * Spring Security의 DaoAuthenticationProvider가 인증 과정에서 호출한다.
     * 메서드 이름은 loadUserByUsername이지만 로그인 식별자는 이메일이다.
     *
     * 이 메서드는 사용자 정보를 제공하는 역할만 담당하며,
     * 비밀번호 비교는 DaoAuthenticationProvider가 PasswordEncoder로 수행한다.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {

        AuthUser user = authMapper.findByEmail(email.trim());

        // 사용자가 없거나 비밀번호가 설정되지 않은 기존 계정은 인증하지 않는다.
        if (user == null
                || user.passwordHash() == null
                || user.passwordHash().isBlank()) {
            throw new UsernameNotFoundException(
                    "이메일 또는 비밀번호를 확인해 주세요."
            );
        }

        return new LoginUser(user);
    }
}