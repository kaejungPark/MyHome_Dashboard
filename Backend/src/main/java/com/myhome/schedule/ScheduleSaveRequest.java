package com.myhome.schedule;

import jakarta.validation.constraints.*;
import org.springframework.cglib.core.Local;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일정 등록·수정에서 공통으로 사용하는 요청 데이터다.
 * 필수값, 유형, 금액과 문자열 길이를 검증한다.
 * 시작일과 마감일의 순서 검증은 Service에서 처리한다.
 */
public record ScheduleSaveRequest(
    // 제목
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자까지 입력할 수 있습니다.")
    String title,

    // 일정 유형: 일반 일정, 납부, 갱신
    @NotBlank(message = "유형 선택은 필수입니다.")
    @Pattern(regexp = "GENERAL|PAYMENT|RENEWAL")
    String scheduleType,

    // 일정 시작일: YYYY-MM-DD
    @NotNull(message = "시작일은 필수입니다.")
    LocalDate startDate,

    // 일정 종료일: YYYY-MM-DD 보험 갱신 기한, 요금 납부 기한
    @NotNull(message = "마감일은 필수입니다.")
    LocalDate dueDate,

    // 완료 여부: true이면 완료, false이면 미완료
    @NotNull(message = "완료 여부 선택은 필수입니다.")
    Boolean completed,

    // 관련 금액: 필요 없으면 null, 입력하면 0 이상
    @DecimalMin(value = "0.00", message = "금액은 0 이상이어야 합니다.")
    @Digits(integer = 16, fraction = 2, message = "금액은 정수 16자리, 소수 2자리까지 입력할 수 있습니다.")
    BigDecimal amount,

    // 메모: 선택 입력
    @Size(max = 2000, message = "메모는 2000자까지 입력할 수 있습니다.")
    String memo

    ) {
}