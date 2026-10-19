package com.myhome.shopping;

import com.myhome.common.response.PageResponse;
import com.myhome.common.util.PageUtils;
import com.myhome.common.validation.DateValidator;
import com.myhome.common.validation.PageValidator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ShoppingService {
    private final ShoppingMapper shoppingMapper;

    public ShoppingService (ShoppingMapper shoppingMapper) {
        this.shoppingMapper = shoppingMapper;
    }

    /**
     * 물품명·구매 여부·구매 예정일 조건으로 사용자의 장보기 목록을 조회한다.
     * 페이지 정보를 검증하고 동일한 조건의 전체 건수와 현재 페이지 항목을 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ShoppingResponse> getShoppings(
            Long userId,
            String name,
            Boolean isCompleted,
            String purchaseDueDate,
            int page,
            int size
    ) {
        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 항목명 앞뒤 공백을 제거한다. 빈 문자열이면 검색 조건을 적용하지 않는다.
        String searchName = name == null ? "" : name.trim();

        // 구매 예정일을 검증하여 날짜로 변환한다. 미입력 시 날짜 검색을 적용하지 않는다.
        LocalDate searchDate = DateValidator.parseOptionalDate(purchaseDueDate);

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 장보기 항목 수를 조회한다.
        long totalCount = shoppingMapper.countAll(userId, searchName, isCompleted, searchDate);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        List<ShoppingResponse> items = offset >= totalCount
                ? List.of()
                : shoppingMapper.findPage(userId, searchName, isCompleted, searchDate, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 로그인한 사용자의 장보기 항목을 등록한다.
     * 구매 여부는 DB 기본값인 미구매로 저장하고 실제 구매일은 비워 둔다.
     */
    @Transactional
    public void createShopping(Long userId, @Valid ShoppingSaveRequest request) {
        int insertRow = shoppingMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * 항목 ID를 검증하고 사용자 소유의 장보기 항목을 수정한다.
     * 구매 여부와 실제 구매일은 유지하며, 수정 대상이 없으면 404를 반환한다.
     */
    @Transactional
    public void updateShopping(Long userId, Long id, @Valid ShoppingSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 장보기 항목입니다."
            );
        }

        int updateRow = shoppingMapper.update(userId, id, request);

        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "장보기 항목을 찾을 수 없습니다."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("장보기 항목 수정중 오류가 발생하였습니다.");
        }

    }

    /**
     * 항목 ID를 검증하고 사용자 소유의 장보기 항목을 삭제한다.
     * 삭제 대상이 없거나 다른 사용자의 항목이면 404를 반환한다.
     */
    @Transactional
    public void deleteShopping(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 장보기 항목 번호입니다."
            );
        }

        int deleteRow = shoppingMapper.delete(userId, id);

        if (deleteRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "장보기 항목을 찾을 수 없습니다."
            );
        }

        if (deleteRow != 1) {
            throw new IllegalStateException("장보기 항목 삭제 중 오류가 발생하였습니다.");
        }
    }

    /**
     * 사용자 소유의 미구매 항목을 구매 완료로 변경한다.
     * 실제 구매일은 한국 기준 오늘로 저장하고 구매 예정일은 유지한다.
     */
    @Transactional
    public void completedShopping(Long userId, Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 장보기 항목입니다."
            );
        }

        // 다른 사용자의 항목은 조회되지 않으므로 없는 항목과 동일하게 처리한다.
        ShoppingResponse shopping = shoppingMapper.findById(userId, id);

        if (shopping == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "장보기 항목을 찾을 수 없습니다."
            );
        }

        if (Boolean.TRUE.equals(shopping.isCompleted())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 구매 완료된 항목입니다."
            );
        }

        LocalDate completedDate = LocalDate.now(ZoneId.of("Asia/Seoul"));

        int updateRow = shoppingMapper.shoppingCompleted(userId, id, completedDate);

        // 조회 이후 다른 요청이 먼저 완료하거나 삭제한 경우 성공으로 처리하지 않는다.
        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "항목 상태가 변경되었습니다. 새로고침 후 다시 시도해 주세요."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("구매 완료 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 사용자 소유의 구매 완료 항목을 미구매 상태로 되돌린다.
     * 실제 구매일은 초기화하고 구매 예정일은 유지한다.
     */
    @Transactional
    public void cancelShopping(
            Long userId, Long id
    ) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 장보기 항목입니다."
            );
        }

        // 다른 사용자의 항목은 조회되지 않으므로 없는 항목과 동일하게 처리한다.
        ShoppingResponse shopping = shoppingMapper.findById(userId, id);

        if (shopping == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "장보기 항목을 찾을 수 없습니다."
            );
        }

        if (Boolean.FALSE.equals(shopping.isCompleted())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "구매 완료된 항목만 완료 취소할 수 있습니다."
            );
        }

        int updateRow = shoppingMapper.shoppingCancel(userId, id);

        // 조회 이후 다른 요청이 먼저 완료 취소하거나 삭제한 경우   성공으로 처리하지 않는다.
        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "항목 상태가 변경되었습니다. 새로고침 후 다시 시도해 주세요."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("구매 취소 처리 중 오류가 발생했습니다.");
        }
    }
}
