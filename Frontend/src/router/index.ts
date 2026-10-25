import { createRouter, createWebHistory } from 'vue-router'
import { ApiError, request } from '../api/client'

/**
 * 화면 접근 여부를 판단할 때 사용하는 로그인 정보다.
 *
 * /api/auth/me 응답에는 ID·이메일·닉네임도 있지만,
 * 라우터에서는 권한과 비밀번호 변경 필요 여부만 사용한다.
 */
interface RouteLoginUser {
  // 임시 로그인은 일반 사용자·관리자 권한과 구분한다.
  role: 'USER' | 'ADMIN' | 'PASSWORD_RESET'

  // true이면 새 비밀번호를 설정하기 전까지 다른 화면 진입을 제한한다.
  passwordChangeRequired: boolean
}

// 각 화면 컴포넌트는 해당 경로에 진입할 때 불러온다.
// 게시글 등록과 수정은 CommunityFormView를 공유하고 경로의 ID 유무로 구분한다.
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'), // 대시보드
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/AdminView.vue'), // 관리자 화면
      meta: { requiresAdmin: true },
    },
    {
      path: '/admin/reports',
      name: 'admin-reports',
      component: () => import('../views/AdminReportView.vue'),
      // 기존 라우터 가드에서 관리자 권한을 검사한다.
      meta: { requiresAdmin: true },
    },
    {
      path: '/admin/hidden-communities',
      name: 'admin-hidden-communities',
      component: () => import('../views/AdminHiddenCommunityView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'), // 로그인 경로
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('../views/SignupView.vue'), // 회원가입
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
    },
    {
      // 로그인하지 않은 사용자가 임시 비밀번호를 요청하는 화면이다.
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('../views/ForgotPasswordView.vue'),
    },
    {
      // 임시 비밀번호로 로그인한 사용자가 새 비밀번호를 설정하는 화면이다.
      // 화면 진입 시 서버 세션을 조회하여 임시 로그인 여부를 확인한다.
      path: '/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue'),
    },
    {
      path: '/expenses',
      name: 'expenses',
      component: () => import('../views/ExpenseView.vue'), // 생활비
    },
    {
      path: '/budgets',
      name: 'budgets',
      component: () => import('../views/BudgetView.vue'), // 월별 예산
    },
    {
      path: '/recurring-expenses',
      name: 'recurring-expenses',
      component: () => import('../views/RecurringExpenseView.vue'), // 고정 지출
    },
    {
      path: '/schedules',
      name: 'schedules',
      component: () => import('../views/ScheduleView.vue'), // 일정
    },
    {
      path: '/household-tasks',
      name: 'household-tasks',
      component: () => import('../views/HouseholdTaskView.vue'), // 집안일·소모품
    },
    {
      path: '/shopping-items',
      name: 'shopping-items',
      component: () => import('../views/ShoppingView.vue'), // 장보기
    },
    {
      path: '/ingredients',
      name: 'ingredients',
      component: () => import('../views/IngredientView.vue'), // 식재료
    },
    {
      path: '/items',
      name: 'items',
      component: () => import('../views/HomeItemView.vue'), // 물품
    },
    {
      path: '/statistics',
      name: 'statistics',
      component: () => import('../views/StatisticsView.vue'), // 통계
    },
    {
      path: '/community',
      name: 'community',
      component: () => import('../views/CommunityView.vue'), // 커뮤니티
    },
    {
      path: '/community/:id',
      name: 'community-detail',
      component: () => import('../views/CommunityDetailView.vue'), // 게시글 상세
    },
    {
      // 등록된 경로와 일치하지 않는 주소는 404 화면으로 연결한다.
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
    {
      path: '/community/new',
      name: 'community-create',
      component: () => import('../views/CommunityFormView.vue'),
    },
    {
      path: '/community/:id/edit',
      name: 'community-edit',
      component: () => import('../views/CommunityFormView.vue'),
    },
  ],

  // 페이지 이동 시 화면 위쪽부터 표시한다.
  scrollBehavior() {
    return { top: 0 }
  },
})

/**
 * 화면 이동 전에 실제 서버 세션을 확인한다.
 *
 * 검사 순서:
 * 1. 로그인 정보 조회 및 응답 검증
 * 2. 임시 로그인 사용자의 비밀번호 변경 화면 이동
 * 3. 일반 로그인 사용자의 변경 전용 화면 접근 처리
 * 4. 관리자 화면 권한 검사
 *
 * 비로그인 사용자의 기존 공개·체험 화면 이용은 유지한다.
 * 서버·통신 오류는 비로그인으로 간주하지 않고 화면 이동을 취소한다.
 *
 * 이 가드는 화면 이동을 안내하는 역할이다.
 * 실제 API 접근 권한은 백엔드 Security 설정과 필터에서 검사한다.
 */
router.beforeEach(async (to) => {
  // 현재 이동하려는 화면의 종류를 확인한다.
  const isChangePasswordPage = to.name === 'change-password'
  const isProfilePage = to.name === 'profile'
  const requiresAdmin = to.meta.requiresAdmin === true

  // 비로그인 사용자에게 허용하지 않을 화면이다.
  // 일반 생활 관리 화면의 체험 기능은 기존처럼 유지한다.
  const requiresLogin = requiresAdmin || isChangePasswordPage || isProfilePage

  try {
    /*
     * 화면을 이동할 때마다 서버의 현재 로그인 상태를 확인한다.
     *
     * App.vue의 로그인 정보만 사용하면 다른 탭에서의 비밀번호 변경,
     * 세션 만료, 임시 비밀번호 재발급 등을 놓칠 수 있다.
     *
     * requestWithDemo()는 사용하지 않는다.
     * 인증·권한 판단에 체험 데이터를 사용해서는 안 된다.
     */
    const user = await request<RouteLoginUser>('/api/auth/me')

    // 정상 HTTP 응답이어도 필요한 데이터가 없으면 이동하지 않는다.
    if (
      !user ||
      typeof user.passwordChangeRequired !== 'boolean' ||
      !['USER', 'ADMIN', 'PASSWORD_RESET'].includes(user.role)
    ) {
      throw new Error('로그인 상태 정보를 확인하지 못했습니다.')
    }

    /*
     * 임시 로그인 상태와 권한이 서로 일치하는지 확인한다.
     *
     * PASSWORD_RESET인데 변경 필요 여부가 false이거나,
     * 변경이 필요한데 USER·ADMIN 권한이면 예상하지 못한 응답이다.
     */
    const isTemporaryLogin = user.role === 'PASSWORD_RESET'

    if (isTemporaryLogin !== user.passwordChangeRequired) {
      throw new Error('로그인 상태 정보가 올바르지 않습니다.')
    }

    /*
     * 임시 로그인 검사를 관리자 검사보다 먼저 수행한다.
     *
     * 임시 로그인 사용자가 대시보드·관리자·로그인·회원가입 등
     * 다른 화면을 요청해도 비밀번호 변경 화면으로 이동시킨다.
     *
     * 취소하려면 변경 화면의 "취소하고 로그아웃"을 사용한다.
     */
    if (user.passwordChangeRequired) {
      // 이미 변경 화면으로 이동 중이면 그대로 허용한다.
      // 이 조건이 없으면 같은 화면으로 계속 리다이렉트될 수 있다.
      if (isChangePasswordPage) {
        return true
      }

      // 제한된 화면이 방문 기록에 남지 않도록 replace로 이동한다.
      return {
        name: 'change-password',
        replace: true,
      }
    }

    /*
     * 일반 로그인 사용자는 임시 비밀번호 변경 전용 화면을 사용하지 않는다.
     * 기존 회원 정보 화면의 비밀번호 변경 팝업을 이용하도록 이동한다.
     */
    if (isChangePasswordPage) {
      return {
        name: 'profile',
        replace: true,
      }
    }

    // 기존 관리자 권한 검사를 유지한다.
    if (requiresAdmin && user.role !== 'ADMIN') {
      window.alert('관리자만 접근할 수 있는 화면입니다.')

      return {
        name: 'dashboard',
        replace: true,
      }
    }

    // 임시 로그인 제한과 관리자 권한 검사를 모두 통과하면 이동한다.
    return true
  } catch (error: unknown) {
    /*
     * 공통 request()는 비로그인·세션 만료의 401 응답을
     * ApiError로 전달한다.
     *
     * 401만 비로그인으로 처리하고,
     * 서버 장애나 통신 오류는 아래에서 별도로 처리한다.
     */
    if (error instanceof ApiError && error.status === 401) {
      if (requiresLogin) {
        window.alert('로그인이 필요합니다. 다시 로그인해 주세요.')

        return {
          name: 'login',
          replace: true,
        }
      }

      // 로그인·회원가입·비밀번호 찾기 및 기존 공개·체험 화면은 허용한다.
      return true
    }

    // 상태를 확인하지 못했는데 임의로 권한을 허용하지 않는다.
    window.alert(
      error instanceof Error
        ? error.message
        : '로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )

    // 이동을 취소한다. 다른 화면을 재요청하지 않아 반복 이동을 방지한다.
    return false
  }
})

export default router
