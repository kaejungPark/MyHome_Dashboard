package com.myhome.common.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

public class DateValidator {

    /**
     * 두 날짜가 모두 있을 때 종료 날짜가 시작 날짜보다 빠른지 검증한다.
     * 같은 날짜는 허용하며, 필수 여부는 요청 DTO에서 별도로 검증한다.
     *
     * @param sDate 기준 날짜
     * @param eDate 종료 날짜
     * @param eMessage 날짜 순서가 잘못됐을 때 사용할 오류 메시지
     */
    public static void validateDateRange(
            final LocalDate sDate,
            final LocalDate eDate,
            final String eMessage
    ) {
        if (sDate == null || eDate == null) {
            return;
        }

        if (eDate.isBefore(sDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    eMessage
            );
        }
    }
}
