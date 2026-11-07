import { ref } from 'vue'

/**
 * 화면마다 독립적인 오류 메시지 상태와 처리 함수를 생성한다.
 * Error 객체의 메시지가 있으면 사용하고, 없으면 기본 안내 문구를 표시한다.
 */
export function useErrorMessage(fallbackMessage = '요청 처리 중 오류가 발생했습니다.') {
  const errorMessage = ref('')

  /**
   * 화면의 catch에서 받은 오류를 개발자 도구에 기록한다.
   * Error에 메시지가 있으면 그 문구를 errorMessage에 담고,
   * 메시지가 비어 있거나 다른 종류의 값이면 기본 안내 문구를 담는다.
   */
  function showError(error: unknown) {
    console.error(error)

    errorMessage.value =
      error instanceof Error && error.message.trim() ? error.message : fallbackMessage
  }

  return {
    errorMessage,
    showError,
  }
}
