package com.myhome.common.response;

/**
 * 모든 API에서 사용하는 공통 응답 형식이다.
 * T는 각 API가 반환하는 데이터의 타입이다.
 */
public record ApiResponse<T>(
        boolean success, // 요청 성공 여부
        T data,          // 응답 데이터: 반환할 데이터가 없으면 null
        String message   // 오류 안내 메시지: 성공 시 null
) {
    /** 성공 응답을 생성한다. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 데이터 없이 오류 메시지를 포함한 실패 응답을 생성한다. */
    public static ApiResponse<Void> failure(String message) {
        return new ApiResponse<>(false, null, message);
    }
}