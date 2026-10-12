package com.myhome.ingredient;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class IngredientService {
    private final IngredientMapper ingredientMapper;

    public IngredientService(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    /**
     * 로그인한 사용자 ID로 범위를 제한해 식재료 목록을 조회한다.
     * */
    @Transactional(readOnly = true)
    public List<IngredientResponse> getIngredients(Long userId) {
        return ingredientMapper.findIngredients(userId);
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
