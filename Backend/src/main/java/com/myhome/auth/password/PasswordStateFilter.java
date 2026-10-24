package com.myhome.auth.password;

import com.myhome.auth.AuthMapper;
import com.myhome.auth.AuthUser;
import com.myhome.auth.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인 이후의 계정 상태와 비밀번호 변경 상태를 검사한다.
 *
 * 일반 로그인:
 * - 회원이 비활성화되거나 인증 버전이 변경되면 이전 세션을 거절한다.
 *
 * 임시 로그인:
 * - 위 조건에 더해 발급 이력의 만료·재발급·사용 완료를 검사한다.
 * - 비밀번호 변경에 필요한 API만 허용한다.
 *
 * @Component로 등록하지 않고 SecurityConfig에서 직접 추가한다.
 */
public class PasswordStateFilter extends OncePerRequestFilter {

    private final AuthMapper authMapper;
    private final PasswordResetService resetService;

    public PasswordStateFilter(
            AuthMapper authMapper,
            PasswordResetService resetService
    ) {
        this.authMapper = authMapper;
        this.resetService = resetService;
    }

    /**
     * 권한 검사 전에 현재 로그인 정보가 여전히 유효한지 확인한다.
     *
     * 비밀번호 변경으로 인증 버전이 증가하면 다른 브라우저의 세션도
     * 다음 요청 시 이 필터에서 거절된다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        // 비로그인 요청의 허용 여부는 기존 Security 접근 규칙에 맡긴다.
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginUser user)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 세션의 과거 상태 대신 DB의 최신 상태와 인증 버전을 확인한다.
        AuthUser current = authMapper.findById(user.getId());

        boolean invalid = current == null
                || !"ACTIVE".equals(current.status())
                || current.authVersion() != user.getAuthVersion();

        // 임시 로그인은 해당 발급 요청도 여전히 유효해야 한다.
        if (!invalid && user.isPasswordChangeRequired()) {
            invalid = !resetService.isSessionValid(user, current);
        }

        if (invalid) {
            // 더 이상 사용할 수 없는 세션과 현재 인증 정보를 제거한다.
            new SecurityContextLogoutHandler()
                    .logout(request, response, authentication);

            writeError(
                    response,
                    401,
                    "로그인이 만료되었습니다. 다시 로그인해 주세요."
            );
            return;
        }

        // 임시 로그인 상태에서 일반 기능 API를 직접 호출하는 것도 차단한다.
        if (user.isPasswordChangeRequired() && !allowed(request)) {
            writeError(
                    response,
                    403,
                    "새 비밀번호로 변경한 후 이용해 주세요."
            );
            return;
        }

        // 상태와 접근 조건을 모두 만족하면 다음 Security 필터로 전달한다.
        filterChain.doFilter(request, response);
    }

    /**
     * 임시 로그인에서 사용할 수 있는 경로와 HTTP 메서드를 검사한다.
     *
     * me: 변경 필요 상태 조회
     * csrf: 비밀번호 변경 요청에 필요한 CSRF 토큰 조회
     * password: 새 비밀번호 저장
     * logout: 임시 로그인 취소
     *
     * 경로뿐 아니라 메서드도 함께 검사한다.
     */
    private boolean allowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        return ("GET".equals(method)
                && ("/api/auth/me".equals(path)
                || "/api/auth/csrf".equals(path)))
                || ("PUT".equals(method)
                && "/api/auth/password".equals(path))
                || ("POST".equals(method)
                && "/api/auth/logout".equals(path));
    }

    /**
     * 필터에서 발생한 거절 응답을 기존 ApiResponse 형태로 반환한다.
     *
     * 컨트롤러 호출 전이므로 GlobalExceptionHandler를 거치지 않는다.
     * message에는 이 클래스에 정의한 고정 문구만 전달한다.
     */
    private void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write(
                "{\"success\":false,\"data\":null,\"message\":\""
                        + message + "\"}"
        );
    }
}