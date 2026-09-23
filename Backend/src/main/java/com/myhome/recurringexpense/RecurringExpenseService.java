package com.myhome.recurringexpense;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

@Service
public class RecurringExpenseService {

    private final RecurringExpenseMapper recurringExpenseMapper;
    private final Long userId;

    public RecurringExpenseService(RecurringExpenseMapper recurringExpenseMapper, @Value("${app.user-id}") Long userId) {
        this.recurringExpenseMapper = recurringExpenseMapper;
        this.userId = userId;
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> getRecurringExpense() {
        return recurringExpenseMapper.findeRecurringExpense(userId);
    }

    /**
     * 고정 지출을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createRecurringExpense(@Valid RecurringExpenseSaveRequest request) {

        LocalDate startMonth = parseMonth(request.startMonth());
        LocalDate endMonth = request.endMonth() == null
                ? null
                : parseMonth(request.endMonth());

        // 종료 월은 시작 월보다 빠를 수 없다.
        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "종료 월은 시작 월 이후여야 합니다."
            );
        }

        int insertRow = recurringExpenseMapper.insert(
                userId, request, startMonth, endMonth
        );

        if (insertRow != 1) {
            throw new IllegalStateException("등록 행 수가 올바르지 않습니다.");
        }
    }

    /**
     * 고정 지출을 수정한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void updateRecurringExpense(Long id, RecurringExpenseSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        LocalDate startMonth = parseMonth(request.startMonth());
        LocalDate endMonth = request.endMonth() == null
                ? null
                : parseMonth(request.endMonth());

        // 종료 월은 시작 월보다 빠를 수 없다.
        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "종료 월은 시작 월 이후여야 합니다."
            );
        }

        int updatedRows = recurringExpenseMapper.update(
                id, userId, request, startMonth, endMonth
        );

        if (updatedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "고정 지출 수정중 오류가 발생하였습니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException("고정 지출 수정중 오류가 발생하였습니다.");
        }
    }

    /**
     * 고정 지출을 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleterecurringExpense(Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 ID입니다."
            );
        }

        int deletedRows = recurringExpenseMapper.delete(id, userId);

        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "고정 지출 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException("고정 지출 삭제 중 오류가 발생하였습니다.");
        }

    }

    /**
     * 선택한 월에 납부할 고정 지출 목록과 합계를 조회한다.
     * 활성 상태이면서 시작·종료 월 범위에 포함되는 항목만 계산한다.
     * 실제 지출(EXPENSE)을 생성하거나 변경하지 않는다.
     */
    @Transactional(readOnly = true)
    public MonthlyResponse getMonthlyExpenses(String monthText) {
        // 기존 월 검증 함수를 사용해 조회 월을 검증한다.
        LocalDate monthStart = parseMonth(monthText);
        YearMonth targetMonth = YearMonth.from(monthStart);

        // 현재 사용자의 고정 지출 목록을 조회한다.
        // 기존 Mapper 메서드 이름에 맞춰 호출한다.
        List<RecurringExpenseResponse> expenses =
                recurringExpenseMapper.findeRecurringExpense(userId);

        List<MonthlyPaymentItem> items = expenses.stream()
                // 비활성 항목은 납부 예정 금액에서 제외한다.
                .filter(RecurringExpenseResponse::active)

                // 시작 월부터 종료 월까지 포함한다.
                // 종료 월이 없으면 시작 월 이후 계속 적용한다.
                .filter(expense -> {
                    YearMonth startMonth = YearMonth.parse(expense.startMonth());
                    YearMonth endMonth = expense.endMonth() == null
                            ? null
                            : YearMonth.parse(expense.endMonth());

                    return !targetMonth.isBefore(startMonth)
                            && (endMonth == null || !targetMonth.isAfter(endMonth));
                })

                // 매월 납부일을 선택한 월의 실제 날짜로 변환한다.
                .map(expense -> {
                    // 예: 9월 31일 → 9월 30일, 2월 31일 → 2월 말일
                    int paymentDay = Math.min(
                            expense.paymentDay(),
                            targetMonth.lengthOfMonth()
                    );

                    LocalDate paymentDate = targetMonth.atDay(paymentDay);

                    return new MonthlyPaymentItem(
                            expense.id(),
                            expense.categoryId(),
                            expense.categoryName(),
                            expense.title(),
                            expense.amount(),
                            paymentDate,
                            expense.paymentMethod()
                    );
                })

                // 납부 예정일이 빠른 순서로 정렬한다.
                // 같은 날짜이면 ID 순서로 정렬한다.
                .sorted(
                        Comparator.comparing(
                                MonthlyPaymentItem::paymentDate
                        ).thenComparing(MonthlyPaymentItem::id)
                )
                .toList();

        // 소수점 오차를 피하기 위해 BigDecimal로 합산한다.
        // 대상 항목이 없으면 합계는 0이다.
        BigDecimal totalAmount = items.stream()
                .map(MonthlyPaymentItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MonthlyResponse(
                targetMonth.toString(),
                totalAmount,
                items
        );
    }

    // YYYY-MM 문자열을 해당 월의 1일로 변환한다.
    private LocalDate parseMonth(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "월은 YYYY-MM 형식이어야 합니다."
            );
        }

        try {
            YearMonth month = YearMonth.parse(value);

            if (month.getYear() < 1 || month.getYear() > 9998) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "지원하지 않는 연도입니다."
                );
            }

            return month.atDay(1);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 월입니다.", ex
            );
        }
    }
}
