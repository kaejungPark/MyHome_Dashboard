package com.myhome.expense;

import com.myhome.category.CategoryMapper;
import java.util.List;

import com.myhome.common.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;

/**
 * 로그인한 사용자의 생활비 조회·등록·수정·삭제를 담당한다.
 * 고정 지출 납부로 생성된 생활비는 직접 수정·삭제하지 못하도록 제한한다.
 */
@Service
public class ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final CategoryMapper categoryMapper;


    public ExpenseService(
            ExpenseMapper expenseMapper,
            CategoryMapper categoryMapper
    ) {
        this.expenseMapper = expenseMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 로그인한 사용자의 지출만 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> getExpenses(
            Long userId,
            int page,
            int size
    ) {
        // 페이지 번호와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 조건으로 전체 지출 수를 조회한다.
        long totalCount = expenseMapper.countAll(userId);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 조회 결과가 없거나 요청한 페이지가 범위를 넘으면 빈 목록을 반환한다.
        List<ExpenseResponse> items = offset >= totalCount
                ? List.of()
                : expenseMapper.findPage(userId, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 카테고리를 확인한 뒤 지출을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createExpense(Long userId, ExpenseCreateRequest request) {
        if (categoryMapper.countById(request.categoryId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "존재하지 않는 카테고리입니다."
            );
        }

        int insertedRows = expenseMapper.insert(userId, request);

        if (insertedRows != 1) {
            throw new IllegalStateException("등록 중 오류가 발생하였습니다.");
        }
    }

    /**
     * 카테고리를 확인하고 본인의 지출을 수정한다.
     * 대상이 없거나 다른 사용자의 지출이면 404를 반환한다.
     */
    @Transactional
    public void updateExpense(Long userId, Long id, ExpenseUpdateRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        /**
         * 고정 지출 납부 기록에 연결된 생활비의 직접 수정·삭제를 차단한다.
         * 연결 여부는 지출 유형이 아니라 실제 납부 기록으로 판단한다.
         * 연결된 생활비를 제거하려면 고정 지출의 납부 취소 기능을 사용해야 한다.
         */
        validateEditableExpense(userId, id);

        if (categoryMapper.countById(request.categoryId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."
            );
        }

        int updatedRows = expenseMapper.update(id, userId, request);

        if (updatedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "지출 수정중 오류가 발생하였습니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException("지출 수정중 오류가 발생하였습니다.");
        }
    }

    /**
     * 본인의 지출만 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleteExpense(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 ID입니다."
            );
        }

        // 고정 지출 납부로 생성된 생활비는 직접 수정·삭제하지 못하게 한다.
        validateEditableExpense(userId, id);

        int deletedRows = expenseMapper.deleteByIdAndUserId(id, userId);

        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "지출 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException("지출 삭제 중 오류가 발생하였습니다.");
        }
    }

    /** 납부로 생성된 생활비는 납부 취소 기능으로만 제거할 수 있다. */
    private void validateEditableExpense(Long userId, Long id) {
        if (expenseMapper.existsRecurringPayment(userId, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "고정 지출 납부로 생성된 생활비입니다. "
                            + "고정 지출 화면에서 납부를 취소해 주세요."
            );
        }
    }
}