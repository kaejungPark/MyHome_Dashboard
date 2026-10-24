package com.myhome.auth.password;

import com.myhome.auth.AuthUserDetailsService;
import com.myhome.auth.LoginUser;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 기존 일반 비밀번호 로그인에 임시 비밀번호 로그인을 추가한다.
 *
 * 같은 /api/auth/login 요청에서 다음 순서로 처리한다.
 * 1. 기존 DaoAuthenticationProvider로 일반 비밀번호 검증
 * 2. 일반 비밀번호 불일치인 경우에만 임시 비밀번호 검증
 * 3. 검증한 방식에 따라 일반 권한 또는 변경 전용 권한 반환
 *
 * 계정 정지 등 다른 인증 실패를 임시 로그인으로 우회하지 않는다.
 */
public class PasswordResetAuthenticationProvider
        implements AuthenticationProvider {

    // 기존 회원 조회·BCrypt 비교·계정 상태 검사를 담당한다.
    private final DaoAuthenticationProvider normalProvider;

    // 임시 비밀번호 만료·실패 횟수·해시 비교를 담당한다.
    private final PasswordResetService resetService;

    public PasswordResetAuthenticationProvider(
            AuthUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            PasswordResetService resetService
    ) {
        this.normalProvider =
                new DaoAuthenticationProvider(userDetailsService);

        this.normalProvider.setPasswordEncoder(passwordEncoder);
        this.resetService = resetService;
    }

    /**
     * 로그인 폼에서 전달된 이메일과 비밀번호를 검증한다.
     *
     * 반환한 Authentication은 Spring Security가 세션에 저장한다.
     * 인증 실패 메시지는 기존 로그인 실패 응답으로 통일한다.
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        // 가입 및 비밀번호 찾기와 동일하게 이메일을 정규화한다.
        String email = authentication.getName()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 비밀번호는 trim하거나 대소문자를 변경하지 않는다.
        String password = authentication.getCredentials() == null
                ? ""
                : authentication.getCredentials().toString();

        // BCrypt 비교 전에 입력 바이트 길이를 제한한다.
        if (password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException(
                    "이메일 또는 비밀번호를 확인해 주세요."
            );
        }

        // 정규화한 이메일로 기존 일반 로그인 처리를 요청한다.
        UsernamePasswordAuthenticationToken normalRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        password
                );

        // 요청에 연결된 인증 부가 정보도 기존 처리로 전달한다.
        normalRequest.setDetails(authentication.getDetails());

        try {
            // 일반 비밀번호가 일치하면 기존 권한으로 즉시 반환한다.
            return normalProvider.authenticate(normalRequest);

        } catch (BadCredentialsException exception) {
            /*
             * 일반 비밀번호가 일치하지 않을 때만 임시 비밀번호를 검사한다.
             * DisabledException 등 계정 상태 예외는 잡지 않으므로
             * 정지된 계정이 임시 비밀번호로 로그인할 수 없다.
             */
            LoginUser loginUser =
                    resetService.authenticateTemporary(email, password);

            // 인증 완료 토큰에는 비밀번호 원문을 보관하지 않는다.
            // 권한은 LoginUser가 가진 ROLE_PASSWORD_RESET만 사용한다.
            UsernamePasswordAuthenticationToken result =
                    UsernamePasswordAuthenticationToken.authenticated(
                            loginUser,
                            null,
                            loginUser.getAuthorities()
                    );

            result.setDetails(authentication.getDetails());

            return result;
        }
    }

    /**
     * 이메일·비밀번호 폼 로그인에서 사용하는 인증 토큰만 처리한다.
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}