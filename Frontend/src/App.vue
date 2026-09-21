<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

// 모든 페이지에서 함께 사용하는 메뉴 목록이다.
const menus = [
  { path: '/', label: '대시보드' },
  { path: '/expenses', label: '생활비' },
  { path: '/schedules', label: '일정' },
  { path: '/items', label: '물품' },
  { path: '/statistics', label: '통계' },
]
</script>

<template>
  <a class="skip-link" href="#main-content">본문으로 이동</a>

  <div class="app-layout">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">MyHome</RouterLink>
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
      <header class="topbar">MyHome Dashboard</header>

      <!-- 주소에 맞는 페이지를 공통 레이아웃 안에 표시한다. -->
      <main id="main-content" class="main-content" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style>
/* 공통 레이아웃과 페이지에 함께 적용하는 기본 스타일이다. */
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
  color: #fff;
  font-size: 28px;
  font-weight: 800;
  text-decoration: none;
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
  padding: 20px 32px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
  font-weight: 700;
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

/* 작은 화면에서는 메뉴를 위쪽에 배치한다. */
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

  .main-content,
  .topbar {
    padding: 20px;
  }
}
</style>