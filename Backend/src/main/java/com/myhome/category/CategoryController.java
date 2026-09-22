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
     * GET /api/categories
     * 카테고리가 없으면 빈 목록을 반환한다.
     * DB 오류는 기존 GlobalExceptionHandler에서 처리한다.
     */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(categoryMapper.findAll());
    }
}