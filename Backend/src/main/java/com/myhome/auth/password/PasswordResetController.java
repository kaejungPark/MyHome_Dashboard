package com.myhome.auth.password;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인하지 않은 사용자의 비밀번호 찾기 요청을 처리한다.
 *
 * 임시 비밀번호 로그인은 기존 /api/auth/login을 사용하고,
 * 새 비밀번호 저장은 기존 /api/auth/password를 사용한다.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    /**
     * POST /api/auth/password-reset: 임시 비밀번호 발송 요청.
     *
     * @Valid로 필수값·이메일 형식·길이를 검사한다.
     * 로그인은 필요하지 않지만 기존 CSRF 검사는 유지한다.
     *
     * 가입 여부, 발급 제한, 메일 발송 실패 여부에 따라
     * 회원 존재가 드러나지 않도록 동일한 안내를 반환한다.
     *
     * 성공 응답은 실제 수신함 도착을 보장하지 않는다.
     */
    @PostMapping
    public ApiResponse<String> send(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        service.send(request);

        return ApiResponse.success(
                "발송 가능한 계정이면 임시 비밀번호가 전송됩니다. "
                        + "메일함을 확인해 주세요. 재요청은 1분 후 가능합니다."
        );
    }
}