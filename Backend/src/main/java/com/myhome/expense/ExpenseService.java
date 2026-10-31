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
     * 검색 조건과 페이지 정보를 검증한 뒤 생활비 목록을 조회한다.
     * 동일한 조건으로 전체 건수를 구해 목록과 페이지 정보를 함께 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> getExpenses(
            Long userId,
            String title,
            Long categoryId,
            String expenseType,
            int page,
            int size
    ) {
        // 페이지 번호와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 검색어 앞뒤 공백을 제거한다. 빈 문자열이면 전체 항목을 조회한다.
        String searchTitle = title == null ? "" : title.trim();

        // 지출 유형의 앞뒤 공백을 제거한다. 빈 값이면 고정·변동 지출을 모두 조회한다.
        String searchType = expenseType == null ? "" : expenseType.trim();

        // 조회 조건에는 전체 조회용 빈 값 또는 FIXED·VARIABLE만 허용한다.
        if (!searchType.isEmpty() && !searchType.matches("FIXED|VARIABLE")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 지출 유형를 선택해 주세요."
            );
        }

        // 현재 페이지를 조회하기 전에 건너뛸 데이터 수를 계산한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 검색 조건에 맞는 전체 건수를 조회한다.
        long totalCount = expenseMapper.countAll(userId, searchTitle, categoryId, searchType);

        // 전체 건수와 페이지 크기로 필요한 페이지 수를 계산한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 조회 결과가 없거나 요청한 페이지가 범위를 넘으면 빈 목록을 반환한다.
        List<ExpenseResponse> items = offset >= totalCount
                ? List.of()
                : expenseMapper.findPage(userId, searchTitle, categoryId, searchType, offset, size);

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
        // 화면에서 전달한 카테고리 ID가 실제로 존재하는지 확인한다.
        if (categoryMapper.countById(request.categoryId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "존재하지 않는 카테고리입니다."
            );
        }

        int insertedRows = expenseMapper.insert(userId, request);

        // 한 건을 등록하는 요청이므로 저장된 행 수도 정확히 1이어야 한다.
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

        // 고정 지출 납부 기록에 연결된 생활비인지 확인한다.
        validateEditableExpense(userId, id);

        if (categoryMapper.countById(request.categoryId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."
            );
        }

        int updatedRows = expenseMapper.update(id, userId, request);

        // ID와 사용자 ID를 함께 확인하므로 대상이 없거나 본인 소유가 아니면 0건이다.
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

        // 삭제된 행이 없으면 대상이 없거나 본인 소유가 아닌 것으로 처리한다.
        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "지출 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException("지출 삭제 중 오류가 발생하였습니다.");
        }
    }

    /**
     * 고정 지출 납부로 생성된 생활비의 직접 수정·삭제를 차단한다.
     * FIXED 유형인지가 아니라 실제 납부 기록에 연결되어 있는지로 판단한다.
     * 연결된 생활비를 제거하려면 고정 지출 화면에서 납부를 취소해야 한다.
     */
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