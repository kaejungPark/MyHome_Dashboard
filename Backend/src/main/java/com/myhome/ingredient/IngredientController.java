package com.myhome.ingredient;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {
    private final IngredientService ingredientService;

    public IngredientController (IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }


    /**
     * 로그인한 사용자의 식재료 목록을 조회한다.
     * 보관 중·소비 완료·폐기 상태를 모두 포함한다.
     */
    @GetMapping
    public ApiResponse<List<IngredientResponse>> getIngredients (@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(ingredientService.getIngredients(loginUser.getId()));
    }

    /**
     * 입력값을 검증한 뒤 식재료를 등록하고 성공 시 201을 반환한다.
     * 소유자 ID는 요청 본문이 아닌 로그인 정보에서 가져온다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createIngredient (
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody IngredientSaveRequest request
    ) {
        ingredientService.createIngredient(loginUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * 식재료 ID와 로그인 사용자 ID를 전달해 본인의 식재료를 수정한다.
     * 소비 완료·폐기 처리는 요청의 status 값으로 함께 처리한다.
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateIngredient (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id,
            @Valid @RequestBody IngredientSaveRequest request
    ) {
        ingredientService.updateIngredient(loginUser.getId(), id,  request);
        return ApiResponse.success(null);
    }

    /**
     * 로그인한 사용자의 식재료를 삭제한다.
     * 폐기 상태로 변경하는 것과 달리 기록 자체를 제거한다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteIngredient (
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable ("id") Long id
    ) {
        ingredientService.deleteIngredient(loginUser.getId(), id);
        return ApiResponse.success(null);
    }

}
