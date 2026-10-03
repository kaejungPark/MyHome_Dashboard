package com.myhome.auth;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
     * 현재 로그인한 사용자의 ID, 이메일, 닉네임을 반환한다.
     *
     * @AuthenticationPrincipal은 Spring Security가 보관한 인증 정보에서
     * LoginUser를 가져온다. 요청으로 사용자 ID를 전달받지 않는다.
     * 매번 DB를 조회하는 방식이므로 오해하지 않도록,
     * 반환 정보는 로그인 시 구성된 사용자 정보임을 명시한다.
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
                        loginUser.getNickname()
                )
        );
    }
}
