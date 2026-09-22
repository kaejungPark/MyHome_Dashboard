package com.myhome.budget;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Service
public class BudgetService {

    private final BudgetMapper budgetMapper;
    private final Long userId;

    public BudgetService(BudgetMapper budgetMapper, @Value("${app.user-id}") Long userId) {
        this.budgetMapper = budgetMapper;
        this.userId =  userId;
    }

    /** 월 예산과 지출 합계를 조회해 잔여 금액을 계산한다. */
    @Transactional(readOnly = true)
    public BudgetResponse getBudget(String monthText) {
        YearMonth month = parseMonth(monthText);
        BigDecimal budget = budgetMapper.findAmount(userId, month.atDay(1)); // BigDecimal 금액을 소수점 오차 없이 계산 하기 위해 사용, atDay 그 달의 첫째날을 가져오기 위해 사용
        BigDecimal spent = budgetMapper.sumExpenses(userId, month.atDay(1), month.plusMonths(1).atDay(1));

        return new BudgetResponse(month.toString(), budget != null, budget, spent, budget == null ? null : budget.subtract(spent));


    }

    // 입력 형식과 SQL Server DATE 범위, 다음 달 계산 가능 여부를 검증한다.
    private YearMonth parseMonth(String value) {
        if(value == null || !value.matches("[0-9]{4}-[0-9]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "월은 yyyy-MM 형식이어야 합니다.");
        }

        try {
            YearMonth month = YearMonth.parse(value);

            if (month.getYear() < 1  || month.getYear() > 9999) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 연도입니다.");
            }

            return month;
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 월입니다."
            );
        }
    }

    /**
     * 월 예산을 등록한다.
     * 사용자·월 중복은 DB의 UNIQUE 제약 조건으로 최종 차단한다.
     */
    @Transactional
    public void createBudget(String monthText, BudgetCreateRequest request) {
        YearMonth month = parseMonth(monthText);

        try {
            int insertedRow = budgetMapper.insert(
                    userId,
                    month.atDay(1),
                    request.amount()
            );

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

    @Transactional
    public void updateBudget(String monthText, BudgetUpdateRequest request) {
        // 수정할 예산의 월을 검증하고 해당 월 1일로 변환한다.
        YearMonth month = parseMonth(monthText);

        int updatedRows = budgetMapper.update(
                userId,
                month.atDay(1),
                request.amount()
        );

        if (updatedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "해당 월에 등록된 예산이 없습니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException("예산 수정 오류가 발생하였습니다.");
        }
    }

    @Transactional
    public void deleteBudget(String monthText) {
        YearMonth month = parseMonth(monthText);

        int deletedRows = budgetMapper.delete(userId, month.atDay(1));

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
