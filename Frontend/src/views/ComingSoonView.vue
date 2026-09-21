<script setup lang="ts">
// 라우터에서 전달받은 메뉴 이름을 화면 제목으로 사용한다.
defineProps<{ title: string }>()
</script>

<template>
  <section>
    <h1>{{ title }}</h1>
    <div class="panel">
      <p>아직 구현하지 않은 기능입니다. 개발 일정에 따라 추가할 예정입니다.</p>
    </div>
  </section>
</template>