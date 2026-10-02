<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { House, CalendarDays, UserRound, LogIn, ChevronDown, Lightbulb } from '@lucide/vue'

// 현재 경로를 기준으로 선택된 메뉴를 표시한다.
const route = useRoute()

// MyHome 하위 메뉴의 열림 상태를 관리한다.
const myHomeMenuOpen = ref(false)

// MyHome 메뉴에서 이동할 생활 관리 화면 목록이다.
const myHomeMenus = [
  { path: '/expenses', label: '생활비' },
  { path: '/schedules', label: '일정' },
  { path: '/items', label: '물품' },
  { path: '/budgets', label: '월별 예산' },
  { path: '/recurring-expenses', label: '고정 지출' },
]

// 현재 화면이 MyHome 메뉴에 속하면 상위 메뉴도 강조한다.
const isMyHomePage = computed(() => myHomeMenus.some((menu) => menu.path === route.path))

// 상단에 표시할 현재 날짜를 관리한다.
const currentDate = ref(new Date())

// 날짜를 한국 시간 기준의 연·월·일 형식으로 표시한다.
const todayLabel = computed(() =>
  currentDate.value.toLocaleDateString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }),
)

// 화면 해제 시 정리할 날짜 갱신 타이머다.
let dateTimer: ReturnType<typeof setInterval> | undefined

// MyHome 메뉴 바깥을 클릭하면 하위 메뉴를 닫는다.
function closeMenuOutside(event: MouseEvent) {
  if (event.target instanceof Element && !event.target.closest('.myhome-menu')) {
    myHomeMenuOpen.value = false
  }
}

// 최초 표시 시 날짜 갱신과 메뉴 바깥 클릭 감지를 시작한다.
onMounted(() => {
  dateTimer = setInterval(() => {
    currentDate.value = new Date()
  }, 60_000)

  document.addEventListener('click', closeMenuOutside)
})

// 화면 해제 시 타이머와 이벤트를 제거한다.
onUnmounted(() => {
  if (dateTimer !== undefined) clearInterval(dateTimer)
  document.removeEventListener('click', closeMenuOutside)
})
</script>
<template>
  <a class="skip-link" href="#main-content">본문으로 이동</a>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-inner">
        <RouterLink to="/" class="brand" @click="myHomeMenuOpen = false">
          <House :size="36" aria-hidden="true" />
          <span>MyHome</span>
        </RouterLink>

        <nav class="top-nav" aria-label="주요 메뉴">
          <RouterLink to="/" class="nav-link" exact-active-class="is-active">대시보드</RouterLink>

          <div class="myhome-menu" @keydown.esc.stop="myHomeMenuOpen = false">
            <button
              type="button"
              class="nav-link menu-trigger"
              :class="{ 'is-active': isMyHomePage }"
              :aria-expanded="myHomeMenuOpen"
              aria-controls="myhome-menu-links"
              @click="myHomeMenuOpen = !myHomeMenuOpen"
            >
              생활 관리
              <ChevronDown :size="16" :class="{ rotated: myHomeMenuOpen }" aria-hidden="true" />
            </button>

            <div v-if="myHomeMenuOpen" id="myhome-menu-links" class="menu-dropdown">
              <RouterLink
                v-for="menu in myHomeMenus"
                :key="menu.path"
                :to="menu.path"
                exact-active-class="is-current"
                @click="myHomeMenuOpen = false"
              >
                {{ menu.label }}
              </RouterLink>
            </div>
          </div>

          <RouterLink to="/statistics" class="nav-link" exact-active-class="is-active">
            통계
          </RouterLink>

          <button type="button" class="nav-link" disabled>
            커뮤니티
            <span class="coming-label">예정</span>
          </button>
        </nav>

        <div class="header-date">
          <CalendarDays :size="19" aria-hidden="true" />
          <span>{{ todayLabel }}</span>
        </div>

        <button type="button" class="mobile-login" disabled aria-label="로그인 준비 중">
          <LogIn :size="16" aria-hidden="true" />
          로그인
        </button>
      </div>
    </header>

    <div class="page-layout">
      <aside class="member-sidebar" aria-label="회원 안내">
        <section class="member-card">
          <div class="member-avatar">
            <UserRound :size="42" aria-hidden="true" />
          </div>

          <h2>나의 자취 생활</h2>
          <p class="member-description">
            생활비부터 일정과 물품까지, MyHome에서 한곳에 관리하세요.
          </p>

          <button type="button" class="login-button" disabled>
            <LogIn :size="18" aria-hidden="true" />
            로그인
          </button>
          <p class="auth-note">회원 기능을 준비하고 있어요.</p>

          <div class="member-links">
            <button type="button" disabled>회원가입</button>
            <span aria-hidden="true">|</span>
            <button type="button" disabled>비밀번호 찾기</button>
          </div>
        </section>
        <section class="guide-card">
          <Lightbulb :size="24" aria-hidden="true" />
          <div>
            <h2>생활을 한곳에서</h2>
            <p>생활비부터 일정과 물품까지 편리하게 관리해 보세요.</p>
          </div>
        </section>
      </aside>
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
  background-color: #f5f7fb;
  line-height: 1.6;
}

button,
input,
select,
textarea {
  font: inherit;
}

a:focus-visible,
button:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 3px;
}

h1 {
  margin-top: 0;
  font-size: 28px;
}

h2 {
  margin-top: 0;
  font-size: 18px;
}

/* 기존 화면에서 사용하는 기본 카드와 버튼 */
.panel {
  margin-top: 24px;
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background-color: #fff;
}

button {
  padding: 10px 16px;
  border: 0;
  border-radius: 8px;
  background-color: #2563eb;
  color: #fff;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.app-layout {
  min-height: 100vh;
}

/* 상단 공통 메뉴 */
.app-header {
  border-bottom: 1px solid #e2e8f0;
  background-color: #fff;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 36px;
  max-width: 1520px;
  min-height: 84px;
  margin: 0 auto;
  padding: 0 32px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  color: #172338;
  font-size: 28px;
  font-weight: 800;
  text-decoration: none;
}

.brand svg {
  color: #2563eb;
}

.top-nav {
  display: flex;
  align-items: stretch;
  gap: 18px;
}

.top-nav .nav-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 84px;
  padding: 0 12px;
  border: 0;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  background-color: transparent;
  color: #334155;
  font-size: 16px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.top-nav .nav-link:hover:not(:disabled),
.top-nav .nav-link.is-active {
  color: #2563eb;
}

.top-nav .nav-link.is-active {
  border-bottom-color: #2563eb;
}

.coming-label {
  color: #64748b;
  font-size: 10px;
  font-weight: 400;
}

.header-date {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  color: #475569;
  font-size: 14px;
  white-space: nowrap;
}

/* 생활 관리 하위 메뉴 */
.myhome-menu {
  position: relative;
}

.menu-trigger svg {
  transition: transform 0.15s;
}

.menu-trigger svg.rotated {
  transform: rotate(180deg);
}

.menu-dropdown {
  position: absolute;
  top: calc(100% - 4px);
  left: 0;
  z-index: 20;
  width: 180px;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background-color: #fff;
  box-shadow: 0 8px 24px rgb(15 23 42 / 10%);
}

.menu-dropdown a {
  display: block;
  padding: 10px 12px;
  border-radius: 6px;
  color: #475569;
  font-size: 14px;
  text-decoration: none;
}

.menu-dropdown a:hover,
.menu-dropdown a.is-current {
  background-color: #eff6ff;
  color: #2563eb;
}

/* 왼쪽 회원 영역과 오른쪽 본문 */
.page-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  align-items: start;
  gap: 28px;
  max-width: 1520px;
  margin: 0 auto;
  padding: 32px;
}

.member-sidebar,
.main-content {
  min-width: 0;
}

.member-card {
  padding: 28px 20px 20px;
  border: 1px solid #dce3ed;
  border-radius: 12px;
  background-color: #fff;
  text-align: center;
}

.member-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  margin: 0 auto 18px;
  border-radius: 50%;
  background-color: #eff6ff;
  color: #2563eb;
}

.member-card h2 {
  margin: 0 0 10px;
  color: #172338;
  font-size: 20px;
  text-align: left;
  word-break: keep-all;
}

.member-description {
  margin: 0 0 24px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
  text-align: left;
  word-break: keep-all;
}

.auth-note {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
  text-align: left;
}

.login-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 48px;
  font-weight: 700;
}

.member-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
  color: #cbd5e1;
}

.member-links button {
  padding: 0;
  background-color: transparent;
  color: #2563eb;
  font-size: 12px;
}

.guide-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background-color: #eff6ff;
}

.guide-card > svg {
  flex-shrink: 0;
  margin-top: 2px;
  color: #2563eb;
}

.guide-card h2 {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 14px;
}

.guide-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.mobile-login {
  display: none;
}

.skip-link {
  position: fixed;
  top: -100px;
  left: 16px;
  z-index: 100;
  padding: 12px;
  background-color: #fff;
  color: #172338;
}

.skip-link:focus {
  top: 12px;
}

/* 중간 너비부터 메뉴를 두 번째 줄로 배치한다. */
@media (max-width: 1100px) {
  .header-inner {
    flex-wrap: wrap;
    gap: 0 24px;
    padding-top: 16px;
  }

  .top-nav {
    order: 3;
    width: 100%;
    flex-wrap: wrap;
  }

  .top-nav .nav-link {
    min-height: 56px;
  }

  .page-layout {
    grid-template-columns: 220px minmax(0, 1fr);
    gap: 20px;
    padding: 24px;
  }
}

/* 모바일에서는 회원 카드 대신 상단 로그인 버튼을 표시한다. */
@media (max-width: 768px) {
  .header-inner {
    padding: 16px 20px 0;
  }

  .brand {
    gap: 8px;
    font-size: 24px;
  }

  .brand svg {
    width: 30px;
    height: 30px;
  }

  .header-date,
  .member-sidebar {
    display: none;
  }

  .mobile-login {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-left: auto;
    padding: 8px 12px;
    font-size: 13px;
  }

  .top-nav {
    gap: 0;
    margin-top: 8px;
  }

  .top-nav .nav-link {
    min-height: 48px;
    padding: 0 8px;
    font-size: 13px;
  }

  .page-layout {
    grid-template-columns: minmax(0, 1fr);
    padding: 24px 16px;
  }
}
</style>
