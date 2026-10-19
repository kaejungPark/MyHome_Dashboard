package com.myhome.common.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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

    /**
     * 선택 입력한 날짜 문자열을 LocalDate로 변환한다.
     * 미입력은 null을 반환하고, 날짜 형식이나 SQL Server DATE 범위가 잘못되면 400을 반환한다.
     */
    public static LocalDate parseOptionalDate (final String dateText) {
        // 날짜는 선택 조건이며, 입력된 경우 날짜 형식과 유효 범위를 검증한다.
        LocalDate searchDate = null;

        if (dateText != null && !dateText.isBlank()) {
            String trimmedDate = dateText.trim();

            try {
                if (!trimmedDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    throw new DateTimeParseException("잘못된 날짜 형식", trimmedDate, 0);
                }

                searchDate = LocalDate.parse(trimmedDate);

                // SQL Server DATE에서 지원하는 연도는 1~9999이다.
                if (searchDate.getYear() < 1) {
                    throw new DateTimeParseException("잘못된 연도", trimmedDate, 0);
                }
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "날짜는 yyyy-MM-dd 형식의 올바른 날짜로 입력해 주세요."
                );
            }
        }
        return searchDate;
    }
}
