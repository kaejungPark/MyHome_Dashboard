<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'

// 공통 화면의 집 로고와 날짜 표시 아이콘
import { House, CalendarDays } from '@lucide/vue'

// 모든 페이지에서 함께 사용하는 메뉴 목록이다.
const menus = [
  { path: '/', label: '대시보드' },
  { path: '/expenses', label: '생활비' },
  { path: '/schedules', label: '일정' },
  { path: '/items', label: '물품' },
  { path: '/statistics', label: '통계' },
  { path: '/budgets', label: '월별 예산' },
  { path: '/recurring-expenses', label: '고정 지출' },
]

// 모든 페이지에서 한국 시간 기준 오늘 날짜를 표시한다.
const currentDate = ref(new Date())

const todayLabel = computed(() =>
  currentDate.value.toLocaleDateString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }),
)

let dateTimer: ReturnType<typeof setInterval> | undefined

// 화면을 계속 켜둔 경우에도 날짜가 바뀌도록 1분마다 갱신한다.
onMounted(() => {
  dateTimer = setInterval(() => {
    currentDate.value = new Date()
  }, 60_000)
})

// 공통 화면이 해제되면 타이머를 정리한다.
onUnmounted(() => {
  if (dateTimer !== undefined) clearInterval(dateTimer)
})
</script>

<template>
  <a class="skip-link" href="#main-content">본문으로 이동</a>

  <div class="app-layout">
    <aside class="sidebar">
      <RouterLink to="/" class="brand">
        <House :size="30" class="brand-icon" aria-hidden="true" />
        <span>MyHome</span>
      </RouterLink>

      <p class="brand-description">나의 생활 관리 대시보드</p>

      <nav aria-label="주요 메뉴">
        <RouterLink
          v-for="menu in menus"
          :key="menu.path"
          :to="menu.path"
          class="nav-link"
          exact-active-class="is-active"
        >
          {{ menu.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <span class="topbar-title">MyHome Dashboard</span>

        <div class="topbar-date">
          <CalendarDays :size="20" :stroke-width="1.5" aria-hidden="true" />
          <span>{{ todayLabel }}</span>
        </div>
      </header>

      <main id="main-content" class="main-content" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: '맑은 고딕', sans-serif;
  color: #1e293b;
  background: #f5f7fb;
  line-height: 1.6;
}

button,
input {
  font: inherit;
}

a:focus-visible,
button:focus-visible {
  outline: 3px solid #f59e0b;
  outline-offset: 3px;
}

.app-layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 230px;
  flex-shrink: 0;
  padding: 28px 20px;
  background: #172338;
  color: #fff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #fff;
  font-size: 28px;
  font-weight: 800;
  text-decoration: none;
}

.brand-icon {
  display: block;
  flex-shrink: 0;
  color: #3b82f6;
}

.brand-description {
  margin: 4px 0 28px;
  color: #cbd5e1;
  font-size: 13px;
}

.nav-link {
  display: block;
  margin-bottom: 8px;
  padding: 12px 16px;
  border-radius: 8px;
  color: #dbe4f0;
  text-decoration: none;
}

.nav-link:hover {
  background: #263750;
}

.nav-link.is-active {
  background: #2563eb;
  color: #fff;
}

.workspace {
  flex: 1;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-height: 72px;
  padding: 16px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.topbar-title {
  font-size: 16px;
  font-weight: 700;
}

.topbar-date {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: auto;
  color: #64748b;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.4;
  white-space: nowrap;
}

.topbar-date svg {
  display: block;
  flex-shrink: 0;
}

.main-content {
  max-width: 1200px;
  padding: 32px;
  margin: 0 auto;
}

h1 {
  margin-top: 0;
  font-size: 28px;
}

h2 {
  margin-top: 0;
  font-size: 18px;
}

.panel {
  margin-top: 24px;
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

button {
  padding: 10px 16px;
  border: 0;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: wait;
}

.skip-link {
  position: fixed;
  top: -100px;
  left: 16px;
  z-index: 10;
  padding: 12px;
  background: #fff;
  color: #172338;
}

.skip-link:focus {
  top: 12px;
}

@media (max-width: 768px) {
  .app-layout {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    padding: 20px;
  }

  .brand-description {
    margin-bottom: 16px;
  }

  .sidebar nav {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .nav-link {
    margin: 0;
    padding: 8px 12px;
  }

  .main-content {
    padding: 20px;
  }

  .topbar {
    flex-wrap: wrap;
    padding: 16px 20px;
  }

  .topbar-date {
    font-size: 13px;
  }
}
</style>
