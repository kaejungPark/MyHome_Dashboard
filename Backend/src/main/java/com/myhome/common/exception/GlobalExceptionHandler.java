package com.myhome.common.exception;

import com.myhome.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * API 요청 처리 중 발생한 예외를 공통 응답 형식으로 변환한다.
 * 상세 오류는 서버 로그에 기록하고, 사용자에게는 안내 메시지를 반환한다.
 */

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Spring MVC가 처리하는 요청 오류의 상태 코드는 유지하고,
     * 응답 본문을 ApiResponse 형식으로 바꾼다.
     */

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null;
        }

        String message = switch (statusCode.value()) {
            case 400 -> "요청한 값이나 형식이 올바르지 않습니다.";
            case 404 -> "요청한 주소를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 요청 방식입니다.";
            case 415 -> "지원하지 않는 데이터 형식입니다.";
            default -> statusCode.is5xxServerError()
                    ? "서버 처리 중 오류가 발생했습니다."
                    : "요청을 처리할 수 없습니다.";
        };

        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC 요청 처리 중 서버 오류 발생", ex);
        }

        // 405 응답의 Allow 등 Spring이 제공한 헤더도 유지한다.
        return new ResponseEntity<>(
                ApiResponse.failure(message),
                response.getHeaders(),
                response.getStatusCode()
        );
    }

    /**
     * DB 조회·저장 오류를 처리한다.
     * SQL이나 내부 연결 정보는 응답에 포함하지 않는다.
     */

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseException(
            DataAccessException ex
    ) {
        log.error("데이터베이스 작업 실패", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "데이터 처리 중 오류가 발생했습니다."
                ));
    }

    /**
     * 별도 처리하지 않은 예외에 대한 마지막 처리 지점이다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception ex
    ) {
        log.error("예상하지 못한 서버 오류 발생", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
                ));
    }
}
