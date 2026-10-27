package com.myhome.budget;

import com.myhome.common.validation.MonthValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 월별 예산을 관리하고 실제 지출을 이용해 잔여 예산을 계산한다.
 * 예산을 등록하지 않은 상태와 0원으로 등록한 상태를 구분한다.
 */
@Service
public class BudgetService {

    private final BudgetMapper budgetMapper;

    public BudgetService(BudgetMapper budgetMapper) {
        this.budgetMapper = budgetMapper;
    }

    /** 월 예산과 지출 합계를 조회해 잔여 금액을 계산한다. */
    @Transactional(readOnly = true)
    public BudgetResponse getBudget(Long userId, String monthText) {
        // 입력한 연도-월 형식을 검증하고 날짜 계산에 사용할 값으로 변환한다.
        YearMonth month = MonthValidator.parseMonth(monthText);
        // 예산 월은 해당 월의 1일로 저장하므로 같은 기준으로 조회한다.
        // 예산이 없으면 null, 0원으로 등록했다면 0을 받는다.
        BigDecimal budget = budgetMapper.findAmount(userId, month.atDay(1));
        // 해당 월 1일 이상, 다음 달 1일 미만의 실제 지출을 합산한다.
        BigDecimal spent = budgetMapper.sumExpenses(userId, month.atDay(1), month.plusMonths(1).atDay(1));
        // 잔여 예산은 예산 금액에서 실제 지출을 뺀 값이다.
        // 예산 미등록이면 계산하지 않고 null, 예산 초과이면 음수를 반환한다.
        return new BudgetResponse(
                month.toString(),
                budget != null,
                budget,
                spent,
                budget == null ? null : budget.subtract(spent)
        );


    }

    /**
     * 월 예산을 등록한다.
     * 사용자·월 중복은 DB의 UNIQUE 제약 조건으로 최종 차단한다.
     */
    @Transactional
    public void createBudget(Long userId, String monthText, BudgetCreateRequest request) {
        YearMonth month = MonthValidator.parseMonth(monthText);

        try {
            int insertedRow = budgetMapper.insert(
                    userId,
                    month.atDay(1),
                    request.amount()
            );

            // 월 예산 한 건을 등록하므로 정상 저장 건수는 1건이다.
            if (insertedRow != 1) {
                throw new IllegalStateException("예산 등록 중 오류가 발생하였습니다.");
            }
        } catch (DuplicateKeyException ex) {
            // 동시에 등록 요청이 들어와도 중복을 409로 처리한다.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 해당 월의 예산이 있습니다.",
                    ex
            );
        }
    }

    /**
     * 현재 사용자의 해당 월 예산 금액을 수정한다.
     * 수정 대상은 사용자 ID와 예산 월로 식별한다.
     * 등록된 예산이 없으면 404를 반환한다.
     */
    @Transactional
    public void updateBudget(Long userId, String monthText, BudgetUpdateRequest request) {
        // 수정할 예산의 월을 검증하고 해당 월 1일로 변환한다.
        YearMonth month = MonthValidator.parseMonth(monthText);

        int updatedRows = budgetMapper.update(
                userId,
                month.atDay(1),
                request.amount()
        );

        // 변경된 행이 없으면 해당 사용자·월의 예산이 없는 것으로 처리한다.
        if (updatedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "해당 월에 등록된 예산이 없습니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException("예산 수정 오류가 발생하였습니다.");
        }
    }

    /**
     * 현재 사용자의 해당 월 예산을 삭제한다.
     * 등록된 예산이 없으면 404를 반환한다.
     * 실제 지출 내역은 삭제하지 않는다.
     */
    @Transactional
    public void deleteBudget(Long userId, String monthText) {
        YearMonth month = MonthValidator.parseMonth(monthText);

        int deletedRows = budgetMapper.delete(userId, month.atDay(1));

        // 삭제된 행이 없으면 해당 사용자·월의 예산이 없는 것으로 처리한다.
        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "해당 월에 등록된 예산이 없습니다."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException("예산 삭제 행 수가 올바르지 않습니다.");
        }
    }
}
