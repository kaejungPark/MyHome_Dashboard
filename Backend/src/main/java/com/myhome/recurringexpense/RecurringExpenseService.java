package com.myhome.recurringexpense;

import com.myhome.common.response.PageResponse;
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
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;

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
    public PageResponse<RecurringExpenseResponse> getRecurringExpense(
            Long userId,
            String title,
            Long categoryId,
            Boolean active,
            int page,
            int size
    ) {

        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 카테고리 미선택은 전체 조회이며, 선택한 ID는 양수여야 한다.
        if (categoryId != null && categoryId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 카테고리를 선택해 주세요."
            );
        }

        // 검색어 앞뒤 공백을 제거한다. 빈 문자열이면 전체 항목을 조회한다.
        String searchTitle = title == null ? "" : title.trim();


        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 조건으로 전체 지출 수를 조회한다.
        long totalCount = recurringExpenseMapper.countAll(userId, searchTitle, categoryId, active);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 조회 결과가 없거나 요청한 페이지가 범위를 넘으면 빈 목록을 반환한다.
        List<RecurringExpenseResponse> items = offset >= totalCount
                ? List.of()
                : recurringExpenseMapper.findPage(userId, searchTitle, categoryId, active, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 선택한 월의 고정 지출을 페이지 단위로 조회한다.
     * 전체 개수와 예정 금액 합계는 현재 페이지가 아닌 월별 전체 대상을 기준으로 계산한다.
     * 납부 기록이 있는 항목은 비활성화되거나 적용 기간이 변경되어도 포함한다.
     */
    @Transactional(readOnly = true)
    public MonthlyResponse getMonthlyExpenses(
            Long userId,
            String monthText,
            int page,
            int size
    ) {

        // 페이지 번호와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 기존 월 검증 함수를 사용해 조회 월을 검증한다.
        LocalDate monthStart = MonthValidator.parseMonth(monthText).atDay(1);
        YearMonth targetMonth = YearMonth.from(monthStart);

        // 곱하기 전에 long으로 변환해 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 월별 조회 조건에 맞는 전체 개수와 전체 금액을 조회한다.
        long totalCount =
                recurringExpenseMapper.countMonthly(userId, monthStart);

        BigDecimal totalAmount =
                recurringExpenseMapper.sumMonthlyAmount(userId, monthStart);

        // 대상이 없으면 예정 금액 합계는 0으로 표시한다.
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 월별 조건과 납부 예정일 정렬을 적용한 뒤 현재 페이지를 조회한다.
        List<MonthlyPaymentItem> items = offset >= totalCount
                ? List.of()
                : recurringExpenseMapper.findMonthlyPage(
                userId,
                monthStart,
                offset,
                size
        );

        PageResponse<MonthlyPaymentItem> pageResponse = new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );

        return new MonthlyResponse(
                targetMonth.toString(),
                totalAmount,
                pageResponse
        );
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
     * 로그인한 사용자의 고정 지출 설정을 삭제한다.
     * 납부 기록이 있으면 이력 보존을 위해 삭제를 차단하고 비활성화를 안내한다.
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

        // 시작·종료 월을 모두 포함한다. 종료 월이 없으면 시작 월 이후의 모든 월을 허용한다.
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

    /**
     * 대시보드에서 사용할 해당 월의 전체 고정 지출 예정 합계를 조회한다.
     */
    @Transactional(readOnly = true)
    public BigDecimal getMonthlyTotalAmount(Long userId, String monthText) {
        LocalDate monthStart = MonthValidator.parseMonth(monthText).atDay(1);
        BigDecimal amount =
                recurringExpenseMapper.sumMonthlyAmount(userId, monthStart);

        return amount == null ? BigDecimal.ZERO : amount;
    }


}
