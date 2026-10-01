<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../api/client'
import { Coins, Target, RefreshCw, Plus, Pencil, Trash2 } from '@lucide/vue' // 월별 예산 화면의 제목과 주요 동작에 사용할 아이콘

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
  <section class="crud-page budget-page">
    <header class="page-heading">
      <div class="page-icon">
        <Coins :size="20" aria-hidden="true" />
      </div>
      <div class="heading">
        <h1>월별 예산</h1>
        <p>월별 예산을 설정하고 지출 금액과 남은 예산을 확인합니다.</p>
      </div>
    </header>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="busy" class="empty-state" role="status">처리 중입니다.</p>

    <section class="card" aria-labelledby="budget-heading">
      <div class="card-heading">
        <div class="heading-title">
          <Target :size="20" aria-hidden="true" />
          <h2 id="budget-heading">월별 예산 조회</h2>
        </div>

        <button type="button" class="btn btn-secondary" :disabled="busy" @click="loadBudget">
          <RefreshCw :size="16" aria-hidden="true" />
          새로고침
        </button>
      </div>

      <div class="form-grid budget-filter">
        <label>
          조회 월
          <input
            v-model="month"
            type="month"
            min="0001-01"
            max="9998-12"
            :disabled="busy"
            @change="loadBudget"
          />
        </label>
      </div>
    </section>

    <template v-if="budget">
      <section class="card" aria-labelledby="budget-summary-heading">
        <div class="card-heading">
          <div class="heading-title">
            <Target :size="20" aria-hidden="true" />
            <h2 id="budget-summary-heading">{{ budget.month }} 예산 현황</h2>
          </div>
          <span class="badge">
            {{ budget.configured ? '예산 설정됨' : '예산 미등록' }}
          </span>
        </div>

        <dl class="budget-summary">
          <div class="summary-item">
            <dt>예산 금액</dt>
            <dd>{{ money(budget.budgetAmount) }}</dd>
          </div>

          <div class="summary-item">
            <dt>사용 금액</dt>
            <dd>{{ money(budget.spentAmount) }}</dd>
          </div>

          <div class="summary-item">
            <dt>잔여 예산</dt>
            <dd :class="{ 'over-budget': (budget.remainingAmount ?? 0) < 0 }">
              {{ money(budget.remainingAmount) }}
            </dd>
          </div>
        </dl>

        <p v-if="!budget.configured" class="budget-note">
          예산을 등록하면 잔여 예산을 확인할 수 있습니다.
        </p>
        <p
          v-else-if="(budget.remainingAmount ?? 0) < 0"
          class="budget-note over-budget"
          role="status"
        >
          예산을 초과했습니다.
        </p>
      </section>

      <form class="card" @submit.prevent="saveBudget">
        <div class="card-heading">
          <div class="heading-title">
            <component :is="budget.configured ? Pencil : Plus" :size="20" aria-hidden="true" />
            <h2>{{ budget.configured ? '예산 수정' : '예산 등록' }}</h2>
          </div>
          <span class="heading-note">* 필수 입력</span>
        </div>

        <fieldset :disabled="busy">
          <legend class="sr-only">월별 예산 금액 입력</legend>

          <div class="form-grid">
            <label>
              예산 금액(원) *
              <input
                v-model="amount"
                type="number"
                min="0"
                step="0.01"
                required
                placeholder="예산 금액을 입력해 주세요"
              />
            </label>
          </div>

          <div class="form-actions">
            <button
              v-if="budget.configured"
              type="button"
              class="btn btn-delete"
              @click="deleteBudget"
            >
              <Trash2 :size="16" aria-hidden="true" />
              삭제
            </button>

            <button
              v-if="budget.configured"
              type="button"
              class="btn btn-secondary"
              @click="resetAmount"
            >
              수정 취소
            </button>

            <button type="submit" class="btn btn-primary">
              <component :is="budget.configured ? Pencil : Plus" :size="16" aria-hidden="true" />
              {{ budget.configured ? '수정 저장' : '등록' }}
            </button>
          </div>
        </fieldset>
      </form>
    </template>
  </section>
</template>

<style scoped>
.budget-page .budget-filter {
  grid-template-columns: minmax(0, 240px);
}
/* 예산 현황을 세 개의 요약 카드로 표시한다. */
.budget-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}

.summary-item {
  min-width: 0;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background-color: #f8fafc;
}

.summary-item dt {
  margin-bottom: 10px;
  color: #64748b;
  font-size: 13px;
}

.summary-item dd {
  margin: 0;
  color: #1e293b;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.budget-note {
  margin: 16px 0 0;
  color: #64748b;
  font-size: 13px;
}

/* 초과 금액과 안내 문구를 강조한다. */
.budget-page .over-budget {
  color: #b91c1c;
}

@media (max-width: 640px) {
  .budget-summary {
    grid-template-columns: 1fr;
  }
}
</style>
