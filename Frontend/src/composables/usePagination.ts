import { ref } from 'vue'
import type { PageResponse } from '../types/page'

/**
 * resetPage() 검색 버튼 클릭 첫 페이지로 이동
 * resetTotals()	목록 조회 시작	이전 전체 건수·페이지 수 초기화
 * applyPage(result)	목록 조회 성공	응답의 페이지 정보 반영
 * canMoveTo(targetPage)	페이지 버튼 클릭	이동 가능한 페이지인지 확인
 * /

/**
 * 목록 하나의 현재 페이지, 페이지 크기, 전체 건수와 전체 페이지 수를 관리한다.
 * 호출할 때마다 별도 상태를 만들므로 한 화면에 목록이 두 개라면 목록별로 호출한다.
 * 예를 들어 고정 지출 화면에서는 관리 목록의 페이지를 넘겨도 월별 납부 목록의 페이지는 유지된다.
 *
 * @param initialSize 한 페이지에 표시할 항목 수. 기본값은 5이며 1~50의 정수를 허용한다.
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
   * 조회 결과에서 페이지 번호, 페이지 크기, 전체 건수, 전체 페이지 수를 가져온다.
   * 화면은 result.items를 목록에 표시하고, 나머지 페이지 정보는 이 함수로 반영한다.
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
