import type { ApiResponse } from '../types/api'

/**
 * API를 호출하고 공통 응답에서 데이터를 반환한다.
 * HTTP 오류 또는 success가 false이면 예외를 발생시킨다.
 * 오류 메시지 표시는 호출한 화면에서 처리한다.
 */
export async function request<T>(url: string, options?: RequestInit): Promise<T | null> {
  const response = await fetch(url, options)
  const result: ApiResponse<T> = await response.json()

  if (!response.ok || !result.success) {
    throw new Error(result.message ?? `요청 실패 (${response.status})`)
  }

  return result.data
}
