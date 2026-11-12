package com.myhome.category;

import com.myhome.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생활비 카테고리 조회 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryMapper categoryMapper;

    public CategoryController(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 생활비와 고정 지출 화면의 선택 목록에 사용할 공통 카테고리를 반환한다.
     * 모든 사용자가 같은 목록을 사용하므로 사용자 ID 없이 조회한다.
     */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(categoryMapper.findAll());
    }
}