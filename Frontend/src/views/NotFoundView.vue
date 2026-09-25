<script setup lang="ts">
// 존재하지 않는 주소에서 홈으로 돌아가는 링크에 사용한다.
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="not-found">
    <p class="error-code">404</p>
    <h1>페이지를 찾을 수 없습니다</h1>
    <p>주소가 변경되었거나 존재하지 않는 페이지입니다.</p>

    <!-- 이전 방문 기록이 없어도 이동할 수 있도록 홈 링크를 제공한다. -->
    <RouterLink class="home-link" to="/">홈으로 이동</RouterLink>
  </section>
</template>

<style scoped>
.not-found {
  padding: 64px 16px;
  text-align: center;
}

.error-code {
  margin: 0;
  color: #2563eb;
  font-size: 72px;
  font-weight: 800;
}

.home-link {
  display: inline-block;
  margin-top: 20px;
  padding: 12px 20px;
  border-radius: 8px;
  background: #2563eb;
  color: white;
  text-decoration: none;
}
</style>
