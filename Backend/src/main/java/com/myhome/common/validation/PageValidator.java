package com.myhome.common.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 목록 조회에서 사용하는 페이지 번호와 페이지 크기를 검증한다.
 */
public final class PageValidator {

    private PageValidator() {
        // 객체를 생성하지 않고 정적 메서드로 사용한다.
    }

    public static void validate(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지는 1 이상, 페이지 크기는 1~50이어야 합니다."
            );
        }
    }

}
