package com.myhome.auth;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;

/**
 * 회원가입, CSRF 토큰 조회, 로그인 사용자 조회 API를 제공한다.
 *
 * 로그인과 로그아웃 요청은 SecurityConfig에 설정된
 * Spring Security 필터가 처리하므로 이 컨트롤러에는 구현하지 않는다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    // 변경된 인증 정보를 현재 HTTP 세션에 명시적으로 저장한다.
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController (AuthService authService) {
        this.authService = authService;
    }

    /**
     * 이메일, 비밀번호, 닉네임을 JSON으로 받아 회원가입을 처리한다.
     *
     * @Valid로 SignupRequest의 필수값·이메일 형식·길이를 검사하고,
     * 서비스에서 이메일 중복 확인과 비밀번호 해시 처리를 수행한다.
     * 가입 성공 시 HTTP 201을 반환하며, 자동으로 로그인하지는 않는다.
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(null));
    }

    /**
     * POST·PUT·PATCH·DELETE 등 데이터 변경 요청에 사용할 CSRF 토큰을 반환한다.
     *
     * 클라이언트는 headerName을 요청 헤더 이름으로,
     * token을 헤더 값으로 전달하며 해당 세션 쿠키도 함께 유지해야 한다.
     * 로그인 또는 로그아웃 후에는 토큰을 다시 조회한다.
     *
     * CSRF 토큰은 요청 위조 방지용이며, 로그인 여부를 증명하는 값은 아니다.
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfToken> getCsrfToken(CsrfToken csrfToken) {
        return ApiResponse.success(csrfToken);
    }

    /**
     * 현재 로그인한 사용자의 ID, 이메일, 닉네임, 권한을 반환한다.
     *
     * @AuthenticationPrincipal로 서버 세션에 보관된 LoginUser를 가져온다.
     * 매 요청마다 DB를 조회하지 않고 로그인 시 구성된 정보를 반환한다.
     * 권한은 USER 또는 ADMIN으로 전달한다.
     * 비로그인 요청은 Spring Security에서 401로 차단한다.
     */
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(
                new LoginResponse(
                        loginUser.getId(),
                        loginUser.getUsername(), // 로그인 식별자로 사용하는 이메일
                        loginUser.getNickname(),
                        loginUser.getRole()
                )
        );
    }

    /**
     * GET /api/auth/profile: 로그인한 사용자의 회원 정보를 조회한다.
     * 요청에서 사용자 ID를 받지 않고 로그인 세션의 ID를 사용한다.
     * 세션의 요약 정보가 아닌 DB에 저장된 회원 정보를 반환한다.
     */
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> getProfile(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(
                authService.getProfile(loginUser.getId())
        );
    }

    /**
     * PUT /api/auth/profile: 본인의 일반 회원 정보를 수정한다.
     * DB 저장 완료 후 현재 세션의 닉네임도 갱신한다.
     */
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 서비스 호출이 정상 종료되면 DB 트랜잭션도 완료된 상태다.
        String nickname = authService.updateProfile(loginUser.getId(), request);

        LoginUser updatedUser = loginUser.withNickname(nickname);

        UsernamePasswordAuthenticationToken updatedAuthentication =
                new UsernamePasswordAuthenticationToken(
                        updatedUser,
                        null,
                        authentication.getAuthorities()
                );

        updatedAuthentication.setDetails(authentication.getDetails());

        // 기존 공유 컨텍스트를 직접 수정하지 않고 새 컨텍스트로 교체한다.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updatedAuthentication);

        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ApiResponse.success(null);
    }

    /**
     * PUT /api/auth/password: 현재 비밀번호를 확인하고 새 비밀번호로 변경한다.
     * 저장 완료 후 현재 세션과 인증 정보를 제거한다.
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody PasswordChangeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 저장 실패 시 예외가 발생하므로 아래 로그아웃 처리는 실행되지 않는다.
        authService.changePassword(loginUser.getId(), request);

        // 현재 세션을 무효화하고 SecurityContext의 인증 정보를 제거한다.
        SecurityContextLogoutHandler logoutHandler =
                new SecurityContextLogoutHandler();

        logoutHandler.logout(httpRequest, httpResponse, authentication);

        // 브라우저에 남은 현재 세션 쿠키도 제거한다.
        new CookieClearingLogoutHandler("JSESSIONID")
                .logout(httpRequest, httpResponse, authentication);

        return ApiResponse.success(null);
    }
}
