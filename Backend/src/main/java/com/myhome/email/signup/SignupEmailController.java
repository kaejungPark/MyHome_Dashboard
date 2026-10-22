package com.myhome.email.signup;

import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 회원가입 전 이메일 인증번호 발송·확인 API를 제공한다.
 * 로그인하지 않은 사용자도 호출할 수 있지만 CSRF 검사는 유지한다.
 */
@RestController
@RequestMapping("/api/auth/signup/email-verifications")
public class SignupEmailController {

    private final SignupEmailService signupEmailService;

    public SignupEmailController(
            SignupEmailService signupEmailService
    ) {
        this.signupEmailService = signupEmailService;
    }

    /**
     * POST /api/auth/signup/email-verifications/send:
     * 입력한 이메일로 회원가입 인증번호를 발송한다.
     * 응답에는 요청 ID·만료 시각·재발송 가능 시각을 포함한다.
     * 인증번호 원문은 반환하지 않는다.
     */
    @PostMapping("/send")
    public ApiResponse<SignupEmailSendResponse> send(
            @Valid @RequestBody SignupEmailSendRequest request
    ) {
        return ApiResponse.success(
                signupEmailService.sendVerification(request)
        );
    }

    /**
     * POST /api/auth/signup/email-verifications/confirm:
     * 이메일·요청 ID·인증번호가 일치하는지 확인한다.
     * 성공하면 회원가입에 사용할 일회용 확인값과 만료 시각을 반환한다.
     * 이 단계에서는 회원을 생성하거나 로그인 처리하지 않는다.
     */
    @PostMapping("/confirm")
    public ApiResponse<SignupEmailConfirmResponse> confirm(
            @Valid @RequestBody SignupEmailConfirmRequest request
    ) {
        return ApiResponse.success(
                signupEmailService.confirmVerification(request)
        );
    }
}