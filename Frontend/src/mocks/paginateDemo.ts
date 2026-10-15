import type { PageResponse } from '../types/page'

// 필터와 정렬이 끝난 샘플 목록을 페이지 단위로 반환한다.
// 원본 배열은 변경하지 않는다.
export function paginateDemo<T>(items: readonly T[], page: number, size: number): PageResponse<T> {
  if (!Number.isInteger(page) || !Number.isInteger(size) || page < 1 || size < 1 || size > 50) {
    throw new Error('페이지는 1 이상, 페이지 크기는 1~50이어야 합니다.')
  }

  const offset = (page - 1) * size

  return {
    items: items.slice(offset, offset + size),
    page,
    size,
    totalCount: items.length,
    totalPages: Math.ceil(items.length / size),
  }
}
