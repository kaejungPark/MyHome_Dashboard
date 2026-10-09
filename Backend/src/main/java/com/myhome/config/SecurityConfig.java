package com.myhome.config;

import jakarta.servlet.http.HttpServlet;
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
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import com.myhome.auth.AuthUserDetailsService;


/**
 * API 접근 규칙과 비밀번호 해시 처리 방식을 설정한다.
 */
@Configuration
public class SecurityConfig  {

    /**
     * API 접근 권한과 폼 방식의 로그인 처리를 설정한다.
     * 로그인 인증과 세션 관리는 Spring Security가 담당한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager
    ) throws Exception {

        http
                // 기존에 만든 DB 조회·비밀번호 검증 설정을 사용한다.
                .authenticationManager(authenticationManager)

                // 회원가입·로그인·CSRF 조회는 비로그인 사용자에게도 허용한다.
                // permitAll()은 인증 요구만 해제하며 CSRF 검사를 생략하지 않는다.
                // 그 외 API는 로그인한 사용자만 접근할 수 있다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login"
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

                        // 로그인 성공 시 페이지 이동 대신 JSON을 반환한다.
                        .successHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("""
                                {"success":true,"data":null,"message":null}
                                """);
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
     * DB 사용자 조회와 비밀번호 해시 비교를 수행할 인증 관리자를 등록한다.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        // 이메일로 사용자 정보를 조회하는 서비스를 연결한다.
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        // 입력한 비밀번호와 DB의 해시를 비교할 인코더를 연결한다.
        provider.setPasswordEncoder(passwordEncoder);

        // 위 인증 처리기를 사용하는 인증 관리자를 반환한다.
        return new ProviderManager(provider);
    }


}
