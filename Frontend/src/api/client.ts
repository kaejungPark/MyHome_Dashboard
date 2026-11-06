import type { ApiResponse } from '../types/api'

/**
 * 서버에서 받은 CSRF 헤더 이름과 토큰이다.
 * 등록·수정·삭제 요청을 보낼 때 이 값을 요청 헤더에 넣는다.
 */
interface CsrfResponse {
  headerName: string
  token: string
}

// HTTP 상태 코드를 함께 전달하는 오류 타입이다.
// 현재 공통 요청에서는 401 응답을 구분할 때 사용한다.
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
 * T는 호출한 화면에서 지정한 응답 데이터의 타입이다.
 * 정상 처리되면 응답의 data를 반환하며, 응답 본문이 없는 204 응답은 null을 반환한다.
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

  // 로그인되지 않았거나 세션이 만료된 경우에는 상태 코드가 담긴 ApiError를 발생시킨다.
  // 호출한 화면은 로그인 안내를 표시하고, requestWithDemo는 이 오류를 체험 데이터 전환에 사용한다.
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

  // 공통 응답 껍데기를 제외한 data만 반환한다. 화면에서 success나 message를 다시 읽지 않는다.
  return result.data
}

/**
 * 조회 성공 시 실제 데이터를, 401 응답일 때만 체험 데이터를 반환한다.
 * 서버·통신 오류와 다른 HTTP 오류는 호출한 화면으로 전달한다.
 * 조회 전용이며 등록·수정·삭제 요청에는 사용하지 않는다.
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
        // 체험 데이터가 필요한 시점에 호출한 화면의 생성 함수를 실행한다.
        data: createSample(),
        isDemo: true,
      }
    }

    throw error
  }
}
