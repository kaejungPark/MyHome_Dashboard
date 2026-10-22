package com.myhome.email;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인한 회원의 이메일 인증번호 발송·확인 API를 제공한다.
 * 사용자 ID는 로그인 정보에서 가져오며 요청 본문으로 받지 않는다.
 * 두 API 모두 로그인과 CSRF 검사가 필요하다.
 */
@RestController
@RequestMapping("/api/email-verifications")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(
            EmailVerificationService emailVerificationService
    ) {
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * POST /api/email-verifications/send:
     * 로그인한 회원의 DB 이메일로 인증번호를 발송한다.
     * 이메일이나 사용자 ID는 요청 본문으로 받지 않는다.
     */
    @PostMapping("/send")
    public ApiResponse<EmailVerificationSendResponse> send(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(
                emailVerificationService.sendVerification(loginUser.getId())
        );
    }

    /**
     * POST /api/email-verifications/confirm:
     * 요청 ID와 인증번호로 본인의 이메일 인증을 완료한다.
     * DB 트랜잭션이 끝난 뒤 검증 결과를 HTTP 응답으로 변환한다.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody EmailVerificationConfirmRequest request
    ) {
        EmailVerificationConfirmResult result =
                emailVerificationService.confirmVerification(
                        loginUser.getId(),
                        request
                );

        return switch (result.status()) {
            case SUCCESS, ALREADY_VERIFIED ->
                    ResponseEntity.ok(ApiResponse.<Void>success(null));

            case INVALID_REQUEST -> failure(
                    HttpStatus.BAD_REQUEST,
                    "인증 요청 정보를 확인할 수 없습니다. 인증번호를 다시 요청해 주세요."
            );

            case EMAIL_CHANGED -> failure(
                    HttpStatus.CONFLICT,
                    "등록된 이메일이 변경됐습니다. 인증번호를 다시 요청해 주세요."
            );

            case EXPIRED -> failure(
                    HttpStatus.GONE,
                    "인증번호가 만료됐습니다. 인증번호를 다시 요청해 주세요."
            );

            case USED -> failure(
                    HttpStatus.CONFLICT,
                    "이미 사용한 인증번호입니다."
            );

            case REVOKED -> failure(
                    HttpStatus.CONFLICT,
                    "사용할 수 없는 인증번호입니다. 가장 최근 발급받은 번호를 확인해 주세요."
            );

            case CODE_MISMATCH -> failure(
                    HttpStatus.BAD_REQUEST,
                    "인증번호가 일치하지 않습니다. 남은 입력 횟수: "
                            + result.remainingAttempts() + "회"
            );

            case ATTEMPTS_EXCEEDED -> failure(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "입력 가능 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."
            );
        };
    }

    /**
     * 커밋이 완료된 인증 결과를 공통 실패 응답으로 변환한다.
     * 이 단계에서 오류 응답을 반환해도 저장된 입력 실패 횟수는 유지된다.
     */
    private ResponseEntity<ApiResponse<Void>> failure(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(message));
    }
}