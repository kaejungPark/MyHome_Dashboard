<script setup lang="ts">
import { onMounted, ref } from 'vue'

interface DbCheckResponse {
  success: boolean
  data: { database: string } | null
  message: string | null
}

const loading = ref(false)
const database = ref('')
const errorMessage = ref('')

// 공통 응답 형식으로 DB 연결 상태를 확인한다.
async function checkDatabase() {
  loading.value = true
  database.value = ''
  errorMessage.value = ''

  try {
    const response = await fetch('/api/check/db')

    if (!response.ok) {
      throw new Error(`서버 요청에 실패했습니다. (${response.status})`)
    }

    const result: DbCheckResponse = await response.json()

    if (!result.success || !result.data?.database) {
      throw new Error(result.message ?? '데이터베이스 정보를 받지 못했습니다.')
    }

    database.value = result.data.database
  } catch (error: unknown) {
    // 오류 원인은 개발자 도구에서 확인하고, 화면에는 안내를 표시한다.
    console.error('DB 연결 확인 실패', error)
    errorMessage.value =
      '연결 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    // 성공과 실패 모두 로딩 상태를 해제한다.
    loading.value = false
  }
}

onMounted(checkDatabase)
</script>

<template>
  <section>
    <h1>대시보드</h1>
    <p>생활비와 일정, 물품 관리 현황을 확인하는 공간입니다.</p>

    <section class="panel" aria-labelledby="connection-title">
      <h2 id="connection-title">개발 환경 연결 확인</h2>

      <p v-if="loading" role="status">연결 상태를 확인하고 있습니다.</p>
      <p v-else-if="errorMessage" role="alert">{{ errorMessage }}</p>
      <p v-else-if="database">연결된 데이터베이스: {{ database }}</p>
      <p v-else>연결 정보가 없습니다.</p>

      <button type="button" :disabled="loading" @click="checkDatabase">
        {{ loading ? '확인 중…' : '다시 확인' }}
      </button>
    </section>
  </section>
</template>