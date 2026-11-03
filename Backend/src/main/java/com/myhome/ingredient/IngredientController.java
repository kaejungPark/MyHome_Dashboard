package com.myhome.ingredient;

import com.myhome.auth.LoginUser;
import com.myhome.common.response.ApiResponse;
import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 식재료 조회·등록·수정·삭제 요청을 받아 서비스에 전달한다.
 * 사용자 ID는 로그인 정보에서 가져와 본인 소유의 식재료만 처리하도록 한다.
 */
@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {
    private final IngredientService ingredientService;

    public IngredientController (IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }


    /**
     * 로그인한 사용자의 식재료를 이름·보관 장소·상태 조건으로 검색한다.
     * 상태를 선택하지 않으면 보관 중·소비 완료·폐기를 모두 조회한다.
     * 검색 결과 목록과 전체 건수, 페이지 정보를 함께 반환한다.
     */
    @GetMapping
    public ApiResponse<PageResponse<IngredientResponse>> getIngredients (
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String storageLocation,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(ingredientService.getIngredients(
                loginUser.getId(),
                keyword,
                storageLocation,
                status,
                page,
                size
        ));
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
