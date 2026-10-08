// T에는 게시글·생활비 등 목록 항목의 타입을 넣는다.
export interface PageResponse<T> {
  items: T[] // 현재 페이지 목록
  page: number // 현재 페이지
  size: number // 페이지당 개수
  totalCount: number // 전체 검색 결과 개수
  totalPages: number // 전체 페이지 수
}
