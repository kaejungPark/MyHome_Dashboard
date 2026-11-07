import type { PageResponse } from '../types/page'

/**
 * 화면에서 검색과 정렬을 마친 체험 목록을 실제 API와 같은 PageResponse 형태로 반환한다.
 * items에는 요청한 페이지의 항목만 담고, totalCount와 totalPages는 검색 결과 전체로 계산한다.
 * 요청한 페이지가 마지막 페이지를 넘으면 items는 빈 배열이 된다.
 */
export function paginateDemo<T>(items: readonly T[], page: number, size: number): PageResponse<T> {
  if (!Number.isInteger(page) || !Number.isInteger(size) || page < 1 || size < 1 || size > 50) {
    throw new Error('페이지는 1 이상, 페이지 크기는 1~50이어야 합니다.')
  }

  // 페이지 번호는 1부터, 배열 위치는 0부터 시작한다. 페이지당 5건이면 2페이지는 배열의 5번 위치부터 읽는다.
  const offset = (page - 1) * size

  return {
    items: items.slice(offset, offset + size),
    page,
    size,
    totalCount: items.length,
    totalPages: Math.ceil(items.length / size),
  }
}
