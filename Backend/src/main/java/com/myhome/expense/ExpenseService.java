package com.myhome.expense;

import com.myhome.category.CategoryMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 지출 조회와 등록에 필요한 업무 처리를 담당한다.
 */
@Service
public class ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final CategoryMapper categoryMapper;
    private final Long userId;

    public ExpenseService(
            ExpenseMapper expenseMapper,
            CategoryMapper categoryMapper,
            @Value("${app.user-id}") Long userId
    ) {
        this.expenseMapper = expenseMapper;
        this.categoryMapper = categoryMapper;
        this.userId = userId;
    }

    /**
     * 설정된 사용자의 지출만 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses() {
        return expenseMapper.findAllByUserId(userId);
    }

    /**
     * 카테고리를 확인한 뒤 지출을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createExpense(ExpenseCreateRequest request) {
        if (categoryMapper.countById(request.categoryId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "존재하지 않는 카테고리입니다."
            );
        }

        int insertedRows = expenseMapper.insert(userId, request);

        if (insertedRows != 1) {
            throw new IllegalStateException("지출 등록 행 수가 올바르지 않습니다.");
        }
    }

    /**
     * 카테고리를 확인하고 본인의 지출을 수정한다.
     * 대상이 없거나 다른 사용자의 지출이면 404를 반환한다.
     */
    @Transactional
    public void updateExpense(Long id, ExpenseUpdateRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 지출 ID입니다."
            );
        }

        if (categoryMapper.countById(request.categoryId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."
            );
        }

        int updatedRows = expenseMapper.update(id, userId, request);

        if (updatedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "지출을 찾을 수 없습니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException("지출 수정 행 수가 올바르지 않습니다.");
        }
    }

    /**
     * 본인의 지출만 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleteExpense(Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 지출 ID입니다."
            );
        }

        int deletedRows = expenseMapper.deleteByIdAndUserId(id, userId);

        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "지출을 찾을 수 없습니다."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException("지출 삭제 행 수가 올바르지 않습니다.");
        }
    }
}