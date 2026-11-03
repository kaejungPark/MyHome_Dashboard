package com.myhome.ingredient;

import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;
import java.util.List;

/**
 * 식재료 조회 조건을 검증하고 등록·수정·삭제 처리를 담당한다.
 * DB 변경 건수를 확인하여 정상적으로 처리되지 않은 경우 예외를 발생시킨다.
 */
@Service
public class IngredientService {
    private final IngredientMapper ingredientMapper;

    public IngredientService(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    /**
     * 페이지 정보와 검색 조건을 검증한 뒤 본인의 식재료를 조회한다.
     * 목록과 동일한 조건으로 전체 건수를 구해 페이지 정보를 계산한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<IngredientResponse> getIngredients(
            Long userId,
            String keyword,
            String storageLocation,
            String status,
            int page,
            int size
    ) {
        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 검색어 앞뒤 공백을 제거한다. 빈 문자열이면 검색 조건을 적용하지 않는다.
        String searchKeyword = keyword == null ? "" : keyword.trim();

        // 미선택이면 전체 장소를 조회한다.
        String location = storageLocation == null ? "" : storageLocation.trim();

        // 전체 조회용 빈 값 또는 냉장·냉동·실온 코드만 허용한다.
        if (!location.isEmpty() && !location.matches("REFRIGERATOR|FREEZER|PANTRY")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 보관 장소를 선택해 주세요."
            );
        }

        // 미선택이면 전체 상태를 조회한다.
        String statusType = status == null ? "" : status.trim();

        // 전체 조회용 빈 값 또는 보관 중·소비 완료·폐기 코드만 허용한다.
        if (!statusType.isEmpty() && !statusType.matches("STORED|CONSUMED|DISPOSED")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 상태를 선택해 주세요."
            );
        }

        // 현재 페이지를 조회하기 전에 건너뛸 데이터 수를 계산한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 식재료 수를 조회한다.
        long totalCount = ingredientMapper.countAll(userId, searchKeyword, location, statusType);

        // 전체 건수와 페이지 크기로 필요한 페이지 수를 계산한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 검색 결과가 없거나 요청한 페이지가 범위를 벗어나면 빈 목록을 반환한다.
        List<IngredientResponse> items = offset >= totalCount
                ? List.of()
                : ingredientMapper.findPage(userId, searchKeyword, location, statusType, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 식재료를 등록하고 저장된 행 수를 확인한다.
     * 정상적으로 한 건이 저장되지 않으면 예외를 발생시켜 롤백한다.
     */
    @Transactional
    public void createIngredient(Long userId, @Valid IngredientSaveRequest request) {
        int insertRow = ingredientMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * 식재료 ID를 검증한 뒤 본인 소유의 식재료를 수정한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void updateIngredient(Long userId, Long id, @Valid IngredientSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 식재료 번호입니다."
            );
        }

        int updateRow = ingredientMapper.update(userId, id, request);

        // 식재료 ID와 사용자 ID가 모두 일치해야 수정되므로 대상이 없거나 본인 소유가 아니면 0건이다.
        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "식재료를 찾을 수 없습니다."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("식재료 수정중 오류가 발생하였습니다.");
        }
    }

    /**
     * 본인 소유의 식재료 기록을 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleteIngredient(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 식재료 번호입니다."
            );
        }

        int deleteRow = ingredientMapper.delete(userId, id);

        // 삭제된 행이 없으면 대상이 없거나 본인 소유가 아닌 것으로 처리한다.
        if (deleteRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "식재료를 찾을 수 없습니다."
            );
        }

        if (deleteRow != 1) {
            throw new IllegalStateException("식재료 삭제 중 오류가 발생하였습니다.");
        }

    }
}
