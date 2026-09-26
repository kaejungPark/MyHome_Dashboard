package com.myhome.category;

/**
 * 카테고리 목록을 화면에 전달하는 응답 데이터다.
 * 단순한 응답 데이터를 표현할 때는 record가 코드가 짧고 목적도 명확해서 사용.
 */
public record CategoryResponse(
        Long id,           // 카테고리 ID
        String name,       // 카테고리명
        Integer sortOrder  // 화면 표시 순서
) {
}