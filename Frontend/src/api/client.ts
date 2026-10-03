import type { ApiResponse } from '../types/api'

interface CsrfResponse {
  headerName: string
  token: string
}

// 화면에서 HTTP 상태 코드로 오류를 구분한다.
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * 공통 API 요청과 응답·오류 처리를 담당한다.
 * 변경 요청에는 현재 세션의 CSRF 토큰을 조회해 헤더에 추가한다.
 * 기존 요청 헤더와 본문은 유지하며, 실패 시 Error를 발생시킨다.
 * 오류 메시지는 호출한 화면에서 표시한다.
 * 직접 fetch를 사용하는 요청에는 이 처리가 적용되지 않는다.
 */
export async function request<T>(url: string, options?: RequestInit): Promise<T | null> {
  const method = (options?.method ?? 'GET').toUpperCase()
  const headers = new Headers(options?.headers)

  // 변경 요청 전에 토큰을 조회한다.
  // 토큰 조회는 GET이므로 이 조건에 다시 들어오지 않는다.
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await request<CsrfResponse>('/api/auth/csrf')

    if (!csrf) {
      throw new Error('요청을 준비하지 못했습니다. 다시 시도해 주세요.')
    }

    headers.set(csrf.headerName, csrf.token)
  }

  // 기존 Content-Type과 Body는 유지한다.
  const response = await fetch(url, {
    ...options,
    headers,
  })

  // 빈 응답이나 JSON이 아닌 오류 응답도 처리한다.
  const text = await response.text()
  let result: ApiResponse<T> | null = null

  if (text) {
    try {
      result = JSON.parse(text) as ApiResponse<T>
    } catch {
      // 아래에서 HTTP 상태에 맞는 오류를 안내한다.
    }
  }

  if (response.status === 401) {
    throw new ApiError(401, result?.message ?? '로그인 후 이용할 수 있습니다.')
  }

  if (response.status === 403) {
    throw new Error('요청이 거절됐습니다. 로그인 상태를 확인하고 다시 시도해 주세요.')
  }

  if (!response.ok) {
    throw new Error(result?.message ?? `요청 처리에 실패했습니다. (${response.status})`)
  }

  // 응답 본문이 없는 정상 처리도 허용한다.
  if (response.status === 204) {
    return null
  }

  if (!result) {
    throw new Error('서버 응답을 확인하지 못했습니다.')
  }

  if (!result.success) {
    throw new Error(result.message ?? '요청 처리에 실패했습니다.')
  }

  return result.data
}

/**
 * 로그인 상태에서는 API 데이터, 비로그인 상태에서는 샘플을 반환한다.
 * 조회 전용이며 서버 오류·통신 오류는 그대로 전달한다.
 */
export async function requestWithDemo<T>(
  url: string,
  createSample: () => T,
): Promise<{ data: T; isDemo: boolean }> {
  try {
    const data = await request<T>(url)

    if (data === null) {
      throw new Error('조회 결과가 없습니다.')
    }

    return { data, isDemo: false }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return {
        data: createSample(),
        isDemo: true,
      }
    }

    throw error
  }
}
