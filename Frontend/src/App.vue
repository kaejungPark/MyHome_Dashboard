<script setup lang="ts">
import { onMounted, ref } from 'vue'

const message = ref('DB 연결 확인 중…')

onMounted(async () => {
  try {
    const response = await fetch('/api/check/db')

    if (!response.ok) {
      throw new Error(`요청 실패: ${response.status}`)
    }

    const data: { database: string } = await response.json()
    message.value = `연결된 데이터베이스: ${data.database}`
  } catch {
    message.value = 'DB 연결 확인에 실패했습니다.'
  }
})
</script>

<template>
  <main>
    <h1>MyHome Dashboard</h1>
    <p>{{ message }}</p>
  </main>
</template>