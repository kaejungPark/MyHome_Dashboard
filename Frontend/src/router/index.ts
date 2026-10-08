import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'), // 대시보드
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
      path: '/schedules',
      name: 'schedules',
      component: () => import('../views/ScheduleView.vue'), // 일정
    },
    {
      path: '/items',
      name: 'items',
      component: () => import('../views/HomeItemView.vue'), // 물품
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

export default router
