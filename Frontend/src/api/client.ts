import type { ApiResponse } from '../types/api'

/**
 * API를 호출하고 공통 응답에서 데이터를 반환한다.
 * HTTP 오류 또는 success가 false이면 예외를 발생시킨다.
 * 오류 메시지 표시는 호출한 화면에서 처리한다.
 */
export async function request<T>(url: string, options?: RequestInit): Promise<T | null> {
  const response = await fetch(url, options)

  // 인증 오류는 JSON 본문이 없을 수 있으므로 먼저 처리한다.
  if (response.status === 401) {
    throw new Error('로그인 후 이용할 수 있습니다.')
  }

  if (response.status === 403) {
    throw new Error('접근 권한이 없거나 보안 토큰이 유효하지 않습니다.')
  }

  const result: ApiResponse<T> = await response.json()

  if (!response.ok || !result.success) {
    throw new Error(result.message ?? `요청 실패 (${response.status})`)
  }

  return result.data
}
