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
import com.myhome.auth.password.PasswordResetService;

/**
 * 회원가입, 로그인 사용자 조회, 회원 정보 수정과 비밀번호 변경을 제공한다.
 * 데이터 변경 요청에 필요한 CSRF 토큰도 반환한다.
 *
 * 로그인·로그아웃 API 요청은 이 컨트롤러가 아니라
 * SecurityConfig에 설정한 Spring Security 필터에서 처리한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // 일반 회원가입·정보 수정·일반 비밀번호 변경을 담당한다.
    private final AuthService authService;

    // 임시 비밀번호 로그인 후 새 비밀번호 변경을 담당한다.
    private final PasswordResetService passwordResetService;

    // 변경된 인증 정보를 현재 HTTP 세션에 명시적으로 저장한다.
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * 이메일 인증을 완료한 사용자의 회원가입을 처리한다.
     * 입력 형식은 @Valid로 검사하고, 이메일 중복과 인증 확인값은 서비스에서 검사한다.
     * 가입 성공 시 HTTP 201을 반환한다.
     * 자동 로그인은 이 API가 아니라 프론트의 별도 로그인 요청으로 처리한다.
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
     * 현재 로그인한 사용자의 요약 정보와 비밀번호 변경 필요 여부를 반환한다.
     *
     * @AuthenticationPrincipal로 세션의 LoginUser를 가져온다.
     * 컨트롤러는 세션 정보를 반환하며, 호출 전 Security 필터에서
     * DB의 현재 계정 상태와 인증 버전을 확인한다.
     *
     * 일반 로그인 권한은 USER·ADMIN이며,
     * 임시 로그인은 PASSWORD_RESET과 변경 필요 여부 true를 반환한다.
     *
     * 비로그인 요청은 Spring Security에서 401로 차단한다.
     */
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(
                new LoginResponse(
                        loginUser.getId(),
                        loginUser.getUsername(),
                        loginUser.getNickname(),
                        loginUser.getRole(),
                        loginUser.isPasswordChangeRequired()
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

        // 저장된 닉네임을 반영한 새 로그인 사용자 정보를 만든다.
        LoginUser updatedUser = loginUser.withNickname(nickname);

        // 로그인 권한은 유지하고 사용자 정보만 교체한다.
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
        // 변경한 인증 정보를 세션에도 저장해 다음 요청에서 사용하도록 한다.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ApiResponse.success(null);
    }

    /**
     * PUT /api/auth/password: 현재 비밀번호를 확인하고 새 비밀번호로 변경한다.
     *
     * 일반 로그인:
     * - 현재 일반 비밀번호를 검증한 후 변경한다.
     *
     * 임시 로그인:
     * - 이메일로 받은 임시 비밀번호를 검증한 후 변경한다.
     * - 사용한 임시 비밀번호 이력도 사용 완료 처리한다.
     *
     * 새 비밀번호 저장 시 인증 버전이 증가한다.
     * 저장 완료 후 현재 세션과 인증 정보를 제거하고,
     * 다른 기존 세션도 다음 요청부터 필터에서 거절된다.
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody PasswordChangeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        /*
         * 로그인 종류는 클라이언트 요청값이 아니라
         * 서버에서 인증하여 세션에 저장한 값으로 판단한다.
         */
        if (loginUser.isPasswordChangeRequired()) {
            passwordResetService.changePassword(loginUser, request);
        } else {
            authService.changePassword(loginUser.getId(), request);
        }

        // 서비스가 정상 반환했으므로 비밀번호 변경 트랜잭션이 완료된 상태다.
        // 저장 실패 시에는 예외가 발생하여 아래 로그아웃 처리를 실행하지 않는다.

        // 현재 세션을 무효화하고 SecurityContext의 인증 정보를 제거한다.
        SecurityContextLogoutHandler logoutHandler =
                new SecurityContextLogoutHandler();

        logoutHandler.logout(
                httpRequest,
                httpResponse,
                authentication
        );

        // 브라우저에 남은 현재 세션 쿠키도 삭제하도록 응답한다.
        new CookieClearingLogoutHandler("JSESSIONID")
                .logout(
                        httpRequest,
                        httpResponse,
                        authentication
                );

        return ApiResponse.success(null);
    }
}
