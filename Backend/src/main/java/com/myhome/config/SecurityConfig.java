package com.myhome.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import com.myhome.auth.AuthUserDetailsService;
import com.myhome.auth.AuthMapper;
import com.myhome.auth.LoginUser;
import com.myhome.auth.password.PasswordResetService;
import com.myhome.auth.password.PasswordResetAuthenticationProvider;
import com.myhome.auth.password.PasswordStateFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * API 접근 규칙과 비밀번호 해시 처리 방식을 설정한다.
 * 관리자 API는 ROLE_ADMIN 권한이 필요하다.
 * 일반 로그인 조건보다 먼저 검사하며, 모든 HTTP 메서드에 적용한다.
 */
@Configuration
public class SecurityConfig  {

    /**
     * API 접근 권한과 폼 방식의 로그인 처리를 설정한다.
     *
     * 로그인 인증과 세션 관리는 Spring Security가 담당한다.
     * 추가 필터에서 인증 버전과 임시 로그인 접근 제한을 검사한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            AuthMapper authMapper,
            PasswordResetService passwordResetService
    ) throws Exception {

        http
                // 기존에 만든 DB 조회·비밀번호 검증 설정을 사용한다.
                .authenticationManager(authenticationManager)

                // 회원가입·가입 전 이메일 인증·로그인·CSRF 조회는 비로그인 사용자에게도 허용한다.
                // 관리자 API는 ADMIN 권한이 필요하고, 그 외 API는 로그인이 필요하다.
                .authorizeHttpRequests(auth -> auth
                        // 회원가입·로그인·가입 전 인증·비밀번호 찾기는 비로그인 사용자도 호출한다.
                        // permitAll()은 로그인 요구만 해제하며 CSRF 검사는 그대로 유지한다.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/signup/email-verifications/send",
                                "/api/auth/signup/email-verifications/confirm",
                                "/api/auth/password-reset"
                        ).permitAll()
                        // CSRF 토큰과 커뮤니티 목록·상세 조회는 비로그인 사용자도 접근할 수 있다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/csrf",
                                "/api/community",
                                "/api/community/{id}"
                        ).permitAll()
                        // 관리자 API는 ADMIN 권한을 가진 사용자만 접근할 수 있다.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()

                )

                // POST /api/auth/login의 폼 데이터(email, password)를 읽어 인증한다.
                // JSON 본문이나 LoginRequest DTO를 사용하는 방식은 아니다.
                // 인증 성공 시 Spring Security가 인증 정보를 세션에 저장하고,
                // 이후 요청에서는 세션 쿠키로 로그인 상태를 확인한다.
                // 이메일과 비밀번호를 폼 데이터로 받아 로그인한다.
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")

                        // 로그인 종류를 응답하여 프론트가 이동할 화면을 판단하도록 한다.
                        .successHandler((request, response, authentication) -> {
                            // 인증이 완료된 사용자 정보에서 로그인 종류를 확인한다.
                            LoginUser loginUser =
                                    (LoginUser) authentication.getPrincipal();

                            // 임시 로그인은 비밀번호 변경만 가능한 상태이므로
                            // 정상 서비스 로그인 시각으로 기록하지 않는다.
                            if (!loginUser.isPasswordChangeRequired()) {
                                authMapper.updateLastLoginDt(loginUser.getId());
                            }

                            response.setStatus(200);
                            response.setContentType("application/json;charset=UTF-8");

                            // true이면 프론트가 비밀번호 변경 화면으로 이동해야 한다.
                            // 화면 이동 외에도 서버 필터에서 일반 API 접근을 차단한다.
                            response.getWriter().write("""
                            {
                              "success": true,
                              "data": {
                                "passwordChangeRequired": %s
                              },
                              "message": null
                            }
                            """.formatted(loginUser.isPasswordChangeRequired()));
                        })

                        // 계정 존재 여부와 관계없이 동일한 실패 메시지를 반환한다.
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("""
                                {"success":false,"data":null,"message":"이메일 또는 비밀번호를 확인해 주세요."}
                                """);
                        })
                )

                // Authorization 헤더의 Basic 인증은 사용하지 않고,
                // 로그인 후 발급·유지되는 세션 쿠키로 인증한다.
                .httpBasic(AbstractHttpConfigurer::disable)

                // 로그아웃 시 세션을 종료하고 세션 쿠키를 삭제한다.
                .logout(logout -> logout
                        // CSRF 보호가 활성화되어 있으므로 POST와 CSRF 토큰이 필요하다.
                        .logoutUrl("/api/auth/logout")
                        // 서버에 저장된 현재 세션을 무효화한다.
                        .invalidateHttpSession(true)
                        // 현재 인증 정보를 제거한다.
                        .clearAuthentication(true)
                        // 클라이언트에 세션 쿠키 삭제를 요청한다.
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("""
                    {"success":true,"data":null,"message":null}
                    """);
                        })
                )

                // 인증이 필요한 API에 비로그인으로 접근하면 401을 반환한다.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                        )
                );

        // 기본 권한 검사 전에 세션 유효성과 임시 로그인 접근 범위를 검사한다.
        // Spring Bean 필터로 별도 등록하지 않고 이 체인에서만 실행한다.
        http.addFilterBefore(
                new PasswordStateFilter(authMapper, passwordResetService),
                AuthorizationFilter.class
        );

        // CSRF 보호와 세션 관리는 기본 설정을 유지한다.
        return http.build();
    }

    /**
     * 회원가입 시 비밀번호를 해시 처리하고,
     * 로그인 시 입력한 비밀번호와 저장된 해시를 비교하는 도구다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 일반 비밀번호와 임시 비밀번호를 순서대로 검증하는
     * 인증 관리자를 등록한다.
     *
     * 내부에서 기존 DaoAuthenticationProvider를 재사용하므로
     * 일반 회원 조회·BCrypt 비교·계정 상태 검사는 유지된다.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            PasswordResetService passwordResetService
    ) {
        PasswordResetAuthenticationProvider provider =
                new PasswordResetAuthenticationProvider(
                        userDetailsService,
                        passwordEncoder,
                        passwordResetService
                );

        // 인증 성공 후 자격 증명 제거 등 ProviderManager의 기본 처리를 사용한다.
        return new ProviderManager(provider);
    }


}
