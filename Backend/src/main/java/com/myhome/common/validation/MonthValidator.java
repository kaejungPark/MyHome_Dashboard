package com.myhome.common.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 여러 Service에서 사용하는 조회·적용 월 검증을 담당한다.
 */
public class MonthValidator {

    /**
     * YYYY-MM 문자열을 검증하고 YearMonth로 변환한다.
     * DB 날짜 범위와 다음 달 계산을 고려해 연도는 1~9998로 제한한다.
     *
     * @param value 검증할 월 문자열
     * @return 검증된 연·월
     */
    public static YearMonth parseMonth(String value) {
        // 값이 없거나 YYYY-MM 형식이 아니면 요청을 거절한다.
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "월은 YYYY-MM 형식이어야 합니다."
            );
        }

        try {
            // 문자열을 연·월을 표현하는 객체로 변환한다.
            YearMonth month = YearMonth.parse(value);

            // 연도 0000과 지원 범위를 벗어난 값을 차단한다.
            if (month.getYear() < 1 || month.getYear() > 9998) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "지원하지 않는 연도입니다."
                );
            }

            return month;
        } catch (DateTimeParseException ex) {
            // 날짜 변환 오류를 잘못된 요청으로 처리한다.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 월입니다.", ex
            );
        }
    }
}