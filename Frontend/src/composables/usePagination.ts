import { ref } from 'vue'
import type { PageResponse } from '../types/page'

/**
 * resetPage() 검색 버튼 클릭 첫 페이지로 이동
 * resetTotals()	목록 조회 시작	이전 전체 건수·페이지 수 초기화
 * applyPage(result)	목록 조회 성공	응답의 페이지 정보 반영
 * canMoveTo(targetPage)	페이지 버튼 클릭	이동 가능한 페이지인지 확인
 * /

/**
 * 목록마다 독립적인 페이지 상태를 생성하고 관리하는 컴포저블
 * @param initialSize 초기 페이지 크기 (기본값: 5)
 */
export function usePagination(initialSize = 5) {
  // 기본 페이지 사이즈 유효성 검증 (정수 및 1~50 범위 체크)
  if (!Number.isInteger(initialSize) || initialSize < 1 || initialSize > 50) {
    throw new Error('페이지 크기는 1~50의 사이여야 합니다.')
  }

  const page = ref(1) // 현재 페이지 번호
  const size = ref(initialSize) // 페이지당 항목 수
  const totalCount = ref(0) // 전체 데이터 개수
  const totalPages = ref(0) // 전체 페이지 수

  /**
   * 검색 조건이 변경되었을 때 페이지를 첫 페이지(1)로 초기화합니다.
   */
  function resetPage() {
    page.value = 1
  }

  /**
   * 현재 페이지 번호는 유지한 채, 조회 결과의 건수와 전체 페이지 수만 초기화합니다.
   */
  function resetTotals() {
    totalCount.value = 0
    totalPages.value = 0
  }

  /**
   * 서버 또는 API 응답의 페이징 정보를 현재 반응형 상태에 동기화합니다.
   * @param result 페이징 응답 데이터 조각 (page, size, totalCount, totalPages)
   * Pick PageResponse에서 페이지 정보 네 개만 사용하기 위해 사용
   */
  function applyPage(
    result: Pick<PageResponse<unknown>, 'page' | 'size' | 'totalCount' | 'totalPages'>,
  ) {
    page.value = result.page
    size.value = result.size
    totalCount.value = result.totalCount
    totalPages.value = result.totalPages
  }

  /**
   * 특정 페이지로 이동 가능한지 검증합니다.
   * (유효한 정수이며, 1 이상이고, 전체 페이지 범위 내이며, 현재 페이지와 다를 때)
   * @param targetPage 이동하려는 목표 페이지 번호
   */
  function canMoveTo(targetPage: number): boolean {
    return (
      Number.isInteger(targetPage) &&
      targetPage >= 1 &&
      targetPage <= totalPages.value &&
      targetPage !== page.value
    )
  }

  return {
    page,
    size,
    totalCount,
    totalPages,
    resetPage,
    resetTotals,
    applyPage,
    canMoveTo,
  }
}
