package com.myhome.common.util;

/**
 * 페이징에 필요한 건너뛸 개수와 전체 페이지 수를 계산한다.
 * page와 size는 PageValidator로 검증한 값을 전달한다.
 */
public final  class PageUtils {

    private PageUtils() {
        // 객체를 생성하지 않고 정적 메서드로 사용한다.
    }

    /**
     * 현재 페이지 앞에서 건너뛸 개수를 계산한다.
     * 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
     */
    public static long calculateOffset(int page, int size) {
        return ((long) page - 1) * size;
    }

    /**
     * 전체 개수로 페이지 수를 계산한다.
     * 나머지가 있으면 한 페이지를 추가하고, 데이터가 없으면 0을 반환한다.
     */
    public static long calculateTotalPages(long totalCount, int size) {
        return totalCount / size + (totalCount % size == 0 ? 0 : 1);
    }
}
