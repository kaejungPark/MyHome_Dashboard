package com.myhome.email;

/**
 * 이메일 인증 확인 결과다.
 * 트랜잭션이 끝난 뒤 컨트롤러에서 HTTP 응답으로 변환한다.
 */
public record EmailVerificationConfirmResult(
        Status status,        // 인증 결과. 컨트롤러에서 HTTP 상태와 안내 문구로 변환한다.
        int remainingAttempts // CODE_MISMATCH일 때 남은 입력 횟수. 다른 결과에서는 0.
) {
    /**
     * 인증 과정에서 발생할 수 있는 처리 결과다.
     * 번호 불일치를 예외 대신 결과로 반환하여 실패 횟수 저장을 유지한다.
     */
    public enum Status {
        SUCCESS,                // 인증 완료
        ALREADY_VERIFIED,       // 이미 인증된 이메일
        INVALID_REQUEST,        // 요청에 해당하는 토큰 없음
        EMAIL_CHANGED,          // 발급 이후 회원 이메일 변경
        EXPIRED,                // 유효기간 만료
        USED,                   // 이미 사용한 토큰
        REVOKED,                // 재발송 등으로 무효화된 토큰
        CODE_MISMATCH,          // 인증번호 불일치
        ATTEMPTS_EXCEEDED       // 입력 실패 횟수 초과
    }
}