import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
    },
    {
      path: '/expenses',
      name: 'expenses',
      component: () => import('../views/ComingSoonView.vue'),
      props: { title: '생활비' },
    },
    {
      path: '/schedules',
      name: 'schedules',
      component: () => import('../views/ComingSoonView.vue'),
      props: { title: '일정' },
    },
    {
      path: '/items',
      name: 'items',
      component: () => import('../views/ComingSoonView.vue'),
      props: { title: '물품' },
    },
    {
      path: '/statistics',
      name: 'statistics',
      component: () => import('../views/ComingSoonView.vue'),
      props: { title: '통계' },
    },
    {
      // 등록된 경로와 일치하지 않는 주소는 404 화면으로 연결한다.
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
    {
      path: '/expenses',
      name: 'expenses',
      component: () => import('../views/ExpenseView.vue'),
    },
  ],

  // 페이지 이동 시 화면 위쪽부터 표시한다.
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router