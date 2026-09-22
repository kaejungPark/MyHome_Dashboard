<script setup lang="ts">
import { onMounted, ref } from 'vue'

// 백엔드 공통 응답 형식이다.
interface ApiResponse<T> {
  success: boolean // 요청 성공 여부
  data: T | null // 응답 데이터
  message: string | null // 오류 안내 메시지
}

// 월별 예산 조회 결과다.
interface Budget {
  month: string // 조회 월 (YYYY-MM)
  configured: boolean // 예산 등록 여부
  budgetAmount: number | null // 예산 금액: 미등록이면 null
  spentAmount: number // 해당 월 지출 합계
  remainingAmount: number | null // 잔여 예산: 초과하면 음수
}

// 첫 화면에서는 사용자 PC의 현재 월을 조회한다.
const now = new Date()
const month = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)

// 화면에서 사용하는 데이터와 처리 상태다.
const budget = ref<Budget | null>(null) // 서버에서 조회한 예산
const amount = ref<string | number>('') // 입력 중인 예산 금액
const busy = ref(false) // 조회·저장·삭제 처리 여부
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 성공 메시지

// API를 호출하고 HTTP 상태와 공통 응답의 성공 여부를 함께 검사한다.
async function request<T>(url: string, options?: RequestInit): Promise<T | null> {
  const response = await fetch(url, options)
  const result: ApiResponse<T> = await response.json()

  if (!response.ok || !result.success) {
    throw new Error(result.message ?? `요청 실패 (${response.status})`)
  }

  return result.data
}

// 발생한 오류를 화면에 표시할 메시지로 변환한다.
function showError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 수정 취소 시 입력값을 마지막으로 조회한 예산 금액으로 되돌린다.
function resetAmount() {
  amount.value = budget.value?.budgetAmount ?? ''
}

// 선택한 월의 최신 예산을 조회한다.
// 조회 실패 시 이전 데이터로 저장하지 못하도록 먼저 화면 데이터를 비운다.
async function refreshBudget() {
  budget.value = null
  amount.value = ''

  const data = await request<Budget>(`/api/budgets/${month.value}`)
  if (!data) throw new Error('예산 조회 결과가 없습니다.')

  budget.value = data
  resetAmount()
}

// 최초 진입, 월 변경, 새로고침 버튼 클릭 시 실행한다.
async function loadBudget() {
  // 다른 요청이 진행 중이면 중복 조회하지 않는다.
  if (busy.value) return

  errorMessage.value = ''
  notice.value = ''
  budget.value = null
  amount.value = ''

  // 월 입력 형식을 검사한다. 세부 날짜 검증은 백엔드에서도 수행한다.
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month.value)) {
    errorMessage.value = '조회할 월을 선택해 주세요.'
    return
  }

  busy.value = true
  try {
    await refreshBudget()
  } catch (error: unknown) {
    showError(error)
  } finally {
    // 성공·실패에 관계없이 입력과 버튼 잠금을 해제한다.
    busy.value = false
  }
}

// 예산이 미등록이면 POST로 등록하고, 등록돼 있으면 PUT으로 수정한다.
async function saveBudget() {
  if (busy.value || !budget.value) return

  errorMessage.value = ''
  notice.value = ''

  // 빈 입력과 숫자가 아닌 값, 음수 금액을 차단한다.
  const value = Number(amount.value)
  if (String(amount.value).trim() === '' || !Number.isFinite(value) || value < 0) {
    errorMessage.value = '0 이상의 예산 금액을 입력해 주세요.'
    return
  }

  const configured = budget.value.configured
  busy.value = true

  try {
    // 월은 URL로 전달하고, 예산 금액은 JSON 본문으로 전달한다.
    await request<null>(`/api/budgets/${month.value}`, {
      method: configured ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: value }),
    })

    notice.value = configured ? '예산이 수정됐습니다.' : '예산이 등록됐습니다.'

    // 저장된 예산과 다시 계산된 잔액을 조회한다.
    await refreshBudget()
  } catch (error: unknown) {
    showError(error)
  } finally {
    busy.value = false
  }
}

// 선택한 월의 예산을 삭제한다. 해당 월의 지출 내역은 삭제하지 않는다.
async function deleteBudget() {
  if (busy.value || !budget.value?.configured) return

  // 사용자가 취소하면 삭제 요청을 보내지 않는다.
  if (!window.confirm(`${month.value} 예산을 삭제하시겠습니까?`)) return

  busy.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    // 삭제 대상 월을 URL에 전달한다. 요청 본문은 필요 없다.
    await request<null>(`/api/budgets/${month.value}`, { method: 'DELETE' })

    notice.value = '예산이 삭제됐습니다. 지출 내역은 유지됩니다.'

    // 삭제 후 미등록 상태와 지출 합계를 다시 조회한다.
    await refreshBudget()
  } catch (error: unknown) {
    showError(error)
  } finally {
    busy.value = false
  }
}

// 금액에 천 단위 구분 기호와 원 단위를 붙인다.
// 0원은 그대로 표시하고, null일 때만 미설정으로 표시한다.
function money(value: number | null) {
  return value === null
    ? '미설정'
    : `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`
}

// 화면이 처음 표시되면 현재 월의 예산을 조회한다.
onMounted(loadBudget)
</script>
<template>
  <section>
    <h1>월별 예산</h1>

    <div class="panel controls">
      <label>
        조회 월
        <input v-model="month" type="month" :disabled="busy" @change="loadBudget" />
      </label>
      <button type="button" :disabled="busy" @click="loadBudget">새로고침</button>
    </div>

    <p v-if="busy" role="status">처리 중입니다.</p>
    <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>

    <template v-if="budget">
      <section class="panel">
        <h2>{{ budget.month }} 예산 현황</h2>
        <dl>
          <dt>예산 금액</dt>
          <dd>{{ money(budget.budgetAmount) }}</dd>
          <dt>사용 금액</dt>
          <dd>{{ money(budget.spentAmount) }}</dd>
          <dt>잔여 예산</dt>
          <dd :class="{ error: (budget.remainingAmount ?? 0) < 0 }">
            {{ money(budget.remainingAmount) }}
          </dd>
        </dl>
        <p v-if="(budget.remainingAmount ?? 0) < 0" class="error">예산을 초과했습니다.</p>
      </section>

      <form class="panel" @submit.prevent="saveBudget">
        <h2>{{ budget.configured ? '예산 수정' : '예산 등록' }}</h2>
        <fieldset :disabled="busy">
          <label>
            예산 금액
            <input v-model="amount" type="number" min="0" step="0.01" required />
          </label>

          <div class="button-group">
            <button type="submit">
              {{ budget.configured ? '수정 저장' : '등록' }}
            </button>
            <button v-if="budget.configured" type="button" @click="resetAmount">수정 취소</button>
            <button v-if="budget.configured" type="button" @click="deleteBudget">삭제</button>
          </div>
        </fieldset>
      </form>
    </template>
  </section>
</template>

<style scoped>
.controls,
.button-group {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
}

.button-group {
  margin-top: 16px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

input {
  padding: 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px 24px;
}

dd {
  margin: 0;
}

.error {
  color: #b91c1c;
}
</style>
