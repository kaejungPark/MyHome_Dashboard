package com.myhome.email;

import java.time.LocalDateTime;

/**
 * DB에서 조회한 이메일 인증·변경·비밀번호 재설정 토큰이다.
 * 검증용 해시를 포함하므로 API 응답이나 로그에 노출하지 않는다.
 * DATETIME2 컬럼에 저장된 시각은 모두 UTC 기준으로 해석한다.
 */
public record EmailToken(
        Long id,                          // 토큰 내부 ID
        String requestId,                 // DB의 UNIQUEIDENTIFIER를 문자열로 조회한다.
        Long userId,                      // 대상 회원 ID
        String purpose,                   // EMAIL_VERIFY / EMAIL_CHANGE / PASSWORD_RESET
        String targetEmail,               // 인증 대상 이메일
        String tokenHash,                 // 인증번호 HMAC 또는 링크 토큰 해시
        int failedAttemptCount,           // 인증번호 입력 실패 횟수
        LocalDateTime expiresDt,          // 만료 시각
        LocalDateTime usedDt,             // 사용 완료 시각, 미사용이면 null
        LocalDateTime revokedDt,          // 무효화 시각, 미폐기이면 null
        LocalDateTime createdDt           // 발급 시각
) {
}