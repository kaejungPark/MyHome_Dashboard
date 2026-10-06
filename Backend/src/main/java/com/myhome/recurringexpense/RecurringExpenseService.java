package com.myhome.recurringexpense;

import com.myhome.common.validation.DateValidator;
import com.myhome.common.validation.MonthValidator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecurringExpenseService {

    private final RecurringExpenseMapper recurringExpenseMapper;
    private static final String DATE_RANGE_MESSAGE = "종료 월은 시작 월 이후여야 합니다.";


    public RecurringExpenseService(RecurringExpenseMapper recurringExpenseMapper) {
        this.recurringExpenseMapper = recurringExpenseMapper;
    }

    /**
     * 현재 사용자의 고정 지출 관리 목록을 조회한다.
     * 수정하거나 다시 활성화할 수 있도록 비활성 항목도 포함한다.
     * 월별 적용 기간 필터링은 납부 예정 조회에서 별도로 수행한다.
     */
    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> getRecurringExpense(Long userId) {
        return recurringExpenseMapper.findeRecurringExpense(userId);
    }

    /**
     * 고정 지출을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createRecurringExpense(Long userId, @Valid RecurringExpenseSaveRequest request) {

        // 요청의 YYYY-MM 문자열을 DB 저장용 해당 월 1일로 변환한다.
        // 종료 월이 없으면 null을 유지한다.
        LocalDate startMonth = MonthValidator.parseMonth(request.startMonth()).atDay(1);
        LocalDate endMonth = request.endMonth() == null
                ? null
                : MonthValidator.parseMonth(request.endMonth()).atDay(1);

        // 종료 월은 시작 월보다 빠를 수 없다.
        DateValidator.validateDateRange(startMonth, endMonth, DATE_RANGE_MESSAGE);

        int insertRow = recurringExpenseMapper.insert(
                userId, request, startMonth, endMonth
        );

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 행 수가 올바르지 않습니다.");
        }
    }

    /**
     * 고정 지출을 수정한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void updateRecurringExpense(Long userId, Long id, RecurringExpenseSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        // 요청의 YYYY-MM 문자열을 DB 저장용 해당 월 1일로 변환한다.
        // 종료 월이 없으면 null을 유지한다.
        LocalDate startMonth = MonthValidator.parseMonth(request.startMonth()).atDay(1);
        LocalDate endMonth = request.endMonth() == null
                ? null
                : MonthValidator.parseMonth(request.endMonth()).atDay(1);


        // 종료 월은 시작 월보다 빠를 수 없다.
        DateValidator.validateDateRange(startMonth, endMonth, DATE_RANGE_MESSAGE);

        int updatedRows = recurringExpenseMapper.update(
                id, userId, request, startMonth, endMonth
        );

        // ID와 사용자 ID 조건에 맞는 대상이 없으면 404를 반환한다.
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
    public void deleterecurringExpense(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 ID입니다."
            );
        }

        // 납부 기록을 보존하기 위해 삭제 대신 비활성화를 안내한다.
        if (recurringExpenseMapper.existsPaymentHistory(userId, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "납부 기록이 있는 고정 지출은 삭제할 수 없습니다. "
                            + "수정 화면에서 비활성화해 주세요."
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
    public MonthlyResponse getMonthlyExpenses(Long userId, String monthText) {

        // 기존 월 검증 함수를 사용해 조회 월을 검증한다.
        LocalDate monthStart = MonthValidator.parseMonth(monthText).atDay(1);
        YearMonth targetMonth = YearMonth.from(monthStart);

        // 고정 지출 ID로 납부 기록을 바로 찾을 수 있도록 Map으로 변환한다.
        Map<Long, RecurringPaymentResponse> payments =
                recurringExpenseMapper.findPaymentsByMonth(userId, monthStart)
                        .stream()
                        .collect(Collectors.toMap(
                                RecurringPaymentResponse::recurringExpenseId,
                                Function.identity()
                        ));

        // 현재 사용자의 고정 지출 목록을 조회한다.
        // 기존 Mapper 메서드 이름에 맞춰 호출한다.
        List<RecurringExpenseResponse> expenses =
                recurringExpenseMapper.findeRecurringExpense(userId);

        List<MonthlyPaymentItem> items = expenses.stream()
                // 비활성 항목은 납부 예정 금액에서 제외한다.
                .filter(expense -> {
                    // 납부 후 비활성화하거나 적용 기간을 바꿔도 납부 이력은 보여준다.
                    if (payments.containsKey(expense.id())) {
                        return true;
                    }

                    YearMonth startMonth = YearMonth.parse(expense.startMonth());
                    YearMonth endMonth = expense.endMonth() == null
                            ? null
                            : YearMonth.parse(expense.endMonth());

                    return expense.active()
                            && !targetMonth.isBefore(startMonth)
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

                    RecurringPaymentResponse payment = payments.get(expense.id());

                    return new MonthlyPaymentItem(
                            expense.id(),
                            expense.categoryId(),
                            expense.categoryName(),
                            expense.title(),
                            expense.amount(),
                            paymentDate,
                            expense.paymentMethod(),
                            payment != null,
                            payment == null ? null : payment.expenseId(),
                            payment == null ? null : payment.paidDate()
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

    /**
     * 해당 월의 고정 지출을 납부 완료 처리한다.
     * 본인 소유 여부, 활성 상태, 적용 기간과 중복 납부 여부를 확인한다.
     * 실제 납부일로 생활비를 생성하고, 해당 생활비와 연결된 납부 기록을 저장한다.
     * 처리 중 예외가 발생하면 생활비와 납부 기록 저장을 모두 롤백한다.
     */
    @Transactional
    public void completePayment(
            Long userId,
            Long id,
            String month,
            RecurringPaymentRequest request
    ) {
        // 조회 월을 검증하고 해당 월 1일로 변환한다.
        LocalDate paymentMonth = MonthValidator.parseMonth(month).atDay(1);

        // id와 userId로 고정 지출을 조회한다.
        RecurringExpenseResponse expense =
                recurringExpenseMapper.findByIdAndUserId(id, userId);

        // 없거나 다른 사용자 소유라면 404를 반환한다.
        if (expense == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "고정 지출을 찾을 수 없습니다."
            );
        }

        // 활성 상태이며 해당 월이 시작·종료 월 범위에 포함되는지 확인한다.
        if (!expense.active()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비활성 상태의 고정 지출은 납부 처리할 수 없습니다."
            );
        }

        YearMonth targetMonth = YearMonth.from(paymentMonth);
        YearMonth startMonth = MonthValidator.parseMonth(expense.startMonth());
        YearMonth endMonth = expense.endMonth() == null
                ? null
                : MonthValidator.parseMonth(expense.endMonth());

        // 시작·종료 월은 포함하며, 종료 월이 없으면 기간 제한을 두지 않는다.
        if (targetMonth.isBefore(startMonth)
                || (endMonth != null && targetMonth.isAfter(endMonth))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 월에 적용되지 않는 고정 지출입니다."
            );
        }
        // 같은 고정 지출의 해당 월 납부 기록이 있는지 확인한다.
        if (recurringExpenseMapper.existsPayment(userId, id, paymentMonth)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 납부 완료한 항목입니다."
            );
        }

        try {
            // 생활비를 등록하고 생성된 생활비 ID를 받는다.
            Long expenseId = recurringExpenseMapper.insertPaidExpense(
                    userId,
                    expense,
                    request.paymentDate()
            );

            if (expenseId == null) {
                throw new IllegalStateException("생활비 등록 중 오류가 발생했습니다.");
            }

            // 고정 지출과 생활비를 연결하는 월별 납부 기록을 저장한다.
            int insertRow = recurringExpenseMapper.insertPayment(
                    userId,
                    id,
                    paymentMonth,
                    expenseId
            );

            if (insertRow != 1) {
                throw new IllegalStateException("납부 기록 등록 중 오류가 발생했습니다.");
            }
        } catch (DuplicateKeyException e) {
            // 동시에 요청되더라도 DB의 UNIQUE 제약조건으로 중복을 차단한다.
            // 예외를 다시 던져 이번 요청에서 생성한 생활비도 함께 롤백한다.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 납부 완료한 항목입니다.",
                    e
            );
        }
    }

    /**
     * 해당 월의 납부 기록과 연결된 생활비를 함께 삭제한다.
     * 처리 중 오류가 발생하면 두 삭제 작업을 모두 롤백한다.
     * 비활성화된 고정 지출도 기존 납부 기록은 취소할 수 있다.
     */
    @Transactional
    public void cancelPayment(Long userId, Long id, String month) {
        LocalDate paymentMonth = MonthValidator.parseMonth(month).atDay(1);

        // 외래 키로 생활비를 참조하므로 납부 기록부터 삭제한다.
        Long expenseId = recurringExpenseMapper.deletePayment(
                userId, id, paymentMonth
        );

        if (expenseId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "취소할 납부 기록을 찾을 수 없습니다."
            );
        }

        // 삭제한 납부 기록에 연결된 생활비만 삭제한다.
        int deletedRows = recurringExpenseMapper.deletePaidExpense(
                userId, expenseId
        );

        if (deletedRows != 1) {
            throw new IllegalStateException(
                    "납부 취소 중 생활비 삭제에 실패했습니다."
            );
        }
    }

}
