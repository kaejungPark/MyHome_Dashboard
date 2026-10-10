import type { Ref } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 체험 상태에서 변경 작업을 시도하면 로그인 화면으로 안내한다.
 * 반환 객체의 canModify()가 true이면 작업을 계속하고, false이면 중단한다.
 * 화면의 변경 동작을 제어하며, 실제 인증·권한 검사는 서버에서 수행한다.
 */
export function useDemoGuard(isDemo: Ref<boolean>) {
  const router = useRouter()

  function canModify(): boolean {
    if (!isDemo.value) return true

    const moveToLogin = window.confirm(
      '내 정보를 저장하고 관리하려면 로그인이 필요합니다. 로그인 화면으로 이동할까요?',
    )

    if (moveToLogin) {
      void router.push('/login')
    }

    return false
  }

  return { canModify }
}
