// 백엔드에서 사용하는 공통 API 응답 형식이다.
export interface ApiResponse<T> {
  success: boolean // 요청 성공 여부
  data: T | null // 응답 데이터
  message: string | null // 오류 안내 메시지
}
