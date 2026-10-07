package com.myhome.common.response;
import java.util.List;

/** 여러 목록 API에서 공통으로 사용하는 페이징 응답이다. */
public record PageResponse<T> (
        List<T> items,     // 현재 페이지의 목록
        int page,          // 현재 페이지 번호: 1부터 시작
        int size,          // 페이지당 조회 개수
        long totalCount,   // 전체 데이터 개수
        long totalPages    // 전체 페이지 수: 데이터가 없으면 0

) {
}

