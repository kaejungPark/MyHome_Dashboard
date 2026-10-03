import type { Ref } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 체험 화면에서 데이터 변경을 시도하면 로그인으로 안내한다.
 * true이면 작업을 계속하고, false이면 중단한다.
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
