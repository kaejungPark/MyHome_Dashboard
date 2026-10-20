import { createRouter, createWebHistory } from 'vue-router'

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
 * 관리자 화면으로 이동하기 전에 서버 세션의 권한을 확인한다.
 * 일반 화면은 그대로 허용하고, 관리자 화면은 ADMIN만 진입할 수 있다.
 */
router.beforeEach(async (to) => {
  // 관리자 전용 화면이 아니면 권한 검사 없이 이동한다.
  if (!to.meta.requiresAdmin) return true

  try {
    // 체험 데이터가 아닌 실제 로그인 정보를 확인한다.
    const response = await fetch('/api/auth/me')

    // 로그인하지 않았거나 세션이 만료되면 로그인 화면으로 이동한다.
    if (response.status === 401) {
      window.alert('로그인이 필요합니다.')
      return { name: 'login' }
    }

    // 서버 오류는 비로그인 상태와 구분한다.
    if (!response.ok) {
      throw new Error('로그인 정보를 확인하지 못했습니다.')
    }

    const result = await response.json()

    // 응답에 사용자 정보가 없으면 진입을 허용하지 않는다.
    if (!result.success || !result.data) {
      throw new Error('로그인 정보를 확인하지 못했습니다.')
    }

    // 일반 사용자는 대시보드로 이동한다.
    if (result.data.role !== 'ADMIN') {
      window.alert('관리자만 접근할 수 있는 화면입니다.')
      return { name: 'dashboard' }
    }

    return true
  } catch {
    // 통신 오류 등으로 권한을 확인하지 못하면 이동을 취소한다.
    window.alert('관리자 권한을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    return false
  }
})

export default router
