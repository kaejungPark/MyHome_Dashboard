<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../api/client'

// 카테고리별 지출 통계
interface CategoryStatistics {
  categoryId: number // 카테고리 ID
  categoryName: string // 카테고리명
  amount: number // 지출 합계
  percentage: number // 전체 지출에서 차지하는 비율
}

// 월별 통계 응답
interface Statistics {
  month: string // 조회 월 (YYYY-MM)
  totalAmount: number // 전체 지출
  configured: boolean // 예산 등록 여부
  budgetAmount: number | null // 월 예산
  remainingAmount: number | null // 잔여 예산
  usageRate: number | null // 예산 사용률
  categories: CategoryStatistics[] // 카테고리별 통계 목록
}

// 화면에서 사용하는 데이터와 처리 상태다.
const statistics = ref<Statistics | null>(null)
const now = new Date() // 첫 화면에서는 사용자 PC의 현재 월을 조회한다.
const selectedMonth = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const loading = ref(false)
const errorMessage = ref('') // 오류 메시지

// 발생한 오류를 화면에 표시할 메시지로 변환한다.
function showError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : '통계 조회 중 오류가 발생했습니다.'
}

// 금액을 천 단위 쉼표로 표시한다.
function formatAmount(amount: number | null) {
  return amount === null ? '—' : amount.toLocaleString('ko-KR')
}

// 선택한 월의 지출·예산·카테고리별 통계를 조회한다.
async function findStatistics() {
  const data = await request<Statistics>(`/api/statistics/${selectedMonth.value}`)

  if (!data) throw new Error('통계된 조회 결과가 없습니다.')

  statistics.value = data
}

// 최초 진입과 조회 버튼 클릭 시 통계를 조회한다.
async function loadStatistics() {
  // 조회 중에는 중복 요청을 막는다.
  if (loading.value) return

  errorMessage.value = ''
  statistics.value = null

  // 조회 월이 YYYY-MM 형식인지 검사한다.
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(selectedMonth.value)) {
    errorMessage.value = '조회할 월을 선택해 주세요.'
    return
  }

  loading.value = true

  try {
    await findStatistics()
  } catch (error: unknown) {
    showError(error)
  } finally {
    // 성공·실패 여부와 관계없이 조회 상태를 해제한다.
    loading.value = false
  }
}

// 화면이 처음 표시되면 현재 월의 예산을 조회한다.
onMounted(loadStatistics)
</script>
<template>
  <main class="statistics-page">
    <header class="page-header">
      <h1>월별 통계</h1>
      <p>월별 지출과 예산 사용 현황을 확인합니다.</p>
    </header>

    <section class="card">
      <form class="month-form" @submit.prevent="loadStatistics">
        <label for="statistics-month">
          조회 월
          <input
            id="statistics-month"
            v-model="selectedMonth"
            type="month"
            min="0001-01"
            max="9998-12"
            required
            :disabled="loading"
          />
        </label>

        <button type="submit" :disabled="loading">
          {{ loading ? '조회 중…' : '조회' }}
        </button>
      </form>
    </section>

    <p v-if="errorMessage" class="error-message" role="alert">
      {{ errorMessage }}
    </p>

    <p v-if="loading" class="status-message" role="status">통계를 불러오는 중입니다.</p>

    <template v-else-if="statistics">
      <h2 class="result-title">{{ statistics.month }} 통계</h2>

      <section class="summary-grid" aria-label="월별 요약">
        <article class="card summary-card">
          <h3>전체 지출</h3>
          <p class="summary-value">{{ formatAmount(statistics.totalAmount) }}원</p>
        </article>

        <article class="card summary-card">
          <h3>월 예산</h3>
          <p class="summary-value">
            {{ statistics.configured ? `${formatAmount(statistics.budgetAmount)}원` : '미등록' }}
          </p>
        </article>

        <article class="card summary-card">
          <h3>잔여 예산</h3>
          <p
            class="summary-value"
            :class="{
              'over-budget': statistics.remainingAmount !== null && statistics.remainingAmount < 0,
            }"
          >
            {{
              statistics.remainingAmount === null
                ? '—'
                : `${formatAmount(statistics.remainingAmount)}원`
            }}
          </p>
          <p
            v-if="statistics.remainingAmount !== null && statistics.remainingAmount < 0"
            class="summary-note over-budget"
          >
            예산을 초과했습니다.
          </p>
        </article>

        <article class="card summary-card">
          <h3>예산 사용률</h3>
          <p
            class="summary-value"
            :class="{
              'over-budget': statistics.usageRate !== null && statistics.usageRate > 100,
            }"
          >
            {{ statistics.usageRate === null ? '—' : `${statistics.usageRate.toFixed(2)}%` }}
          </p>
          <p v-if="statistics.configured && statistics.budgetAmount === 0" class="summary-note">
            예산이 0원이므로 사용률을 계산하지 않습니다.
          </p>
        </article>
      </section>

      <p v-if="!statistics.configured" class="info-message">
        해당 월의 예산이 등록되지 않았습니다.
        <RouterLink to="/budgets">예산 등록하기</RouterLink>
      </p>

      <section class="card category-section">
        <div class="section-header">
          <h2>카테고리별 지출</h2>
          <span>{{ statistics.categories.length }}개 카테고리</span>
        </div>

        <p v-if="statistics.categories.length === 0" class="empty-message">
          해당 월에 등록된 지출이 없습니다.
        </p>

        <div v-else class="table-wrapper">
          <table>
            <caption class="sr-only">
              {{
                statistics.month
              }}
              카테고리별 지출 금액과 비율
            </caption>
            <thead>
              <tr>
                <th scope="col">카테고리</th>
                <th scope="col" class="amount-column">지출 금액</th>
                <th scope="col" class="percentage-column">비율</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="category in statistics.categories" :key="category.categoryId">
                <td>{{ category.categoryName }}</td>
                <td class="amount-column">{{ formatAmount(category.amount) }}원</td>
                <td class="percentage-column">
                  <div class="percentage-cell">
                    <div class="percentage-track" aria-hidden="true">
                      <div
                        class="percentage-fill"
                        :style="{
                          width: `${Math.min(100, Math.max(0, category.percentage))}%`,
                        }"
                      ></div>
                    </div>
                    <span>{{ category.percentage.toFixed(2) }}%</span>
                  </div>
                </td>
              </tr>
            </tbody>

            <tfoot>
              <tr>
                <th scope="row">합계</th>
                <td class="amount-column">{{ formatAmount(statistics.totalAmount) }}원</td>
                <td></td>
              </tr>
            </tfoot>
          </table>

          <p class="table-note">
            비율은 소수점 둘째 자리에서 표시하며, 반올림으로 합계가 100%와 다를 수 있습니다.
          </p>
        </div>
      </section>
    </template>

    <p v-else-if="!errorMessage" class="status-message">조회할 월을 선택해 주세요.</p>
  </main>
</template>

<style scoped>
.statistics-page {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px 24px 48px;
  color: #1e293b;
}

.statistics-page,
.statistics-page * {
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 700;
}

.page-header p {
  margin: 0;
  color: #64748b;
  line-height: 1.6;
}

.card {
  padding: 24px;
  background: #fff;
  border: 1px solid #dce3ed;
  border-radius: 12px;
}

.month-form {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}

.month-form label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.month-form input {
  width: 220px;
  min-height: 42px;
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #1e293b;
  font: inherit;
}

.month-form button {
  min-height: 42px;
  padding: 9px 22px;
  border: none;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.month-form button:hover:not(:disabled) {
  background: #1d4ed8;
}

.month-form button:disabled,
.month-form input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.month-form input:focus-visible,
.month-form button:focus-visible,
.info-message a:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 3px;
}

.result-title {
  margin: 28px 0 16px;
  font-size: 20px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  min-width: 0;
  padding: 20px;
}

.summary-card h3 {
  margin: 0 0 14px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}

.summary-value {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.summary-note {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.over-budget {
  color: #dc2626;
}

.info-message {
  margin: 16px 0 0;
  padding: 14px 16px;
  border-radius: 8px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 14px;
  line-height: 1.6;
}

.info-message a {
  margin-left: 8px;
  color: #1d4ed8;
  text-decoration: underline;
}

.error-message {
  margin: 16px 0;
  padding: 14px 16px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
}

.status-message,
.empty-message {
  padding: 28px 12px;
  color: #64748b;
  text-align: center;
}

.category-section {
  margin-top: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.section-header h2 {
  margin: 0;
  font-size: 18px;
}

.section-header span {
  color: #64748b;
  font-size: 13px;
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 16px 12px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
}

thead th {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
}

.amount-column {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.percentage-column {
  width: 38%;
  text-align: right;
}

.percentage-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.percentage-track {
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.percentage-fill {
  height: 100%;
  border-radius: inherit;
  background: #2563eb;
}

.percentage-cell span {
  min-width: 68px;
  font-variant-numeric: tabular-nums;
}

tfoot th,
tfoot td {
  border-bottom: none;
  background: #f8fafc;
  font-weight: 700;
}

.table-note {
  margin: 16px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .statistics-page {
    padding: 20px 12px 32px;
  }

  .page-header h1 {
    font-size: 24px;
  }

  .card {
    padding: 16px;
  }

  .month-form label {
    flex: 1;
    min-width: 0;
  }

  .month-form input {
    width: 100%;
    min-width: 0;
  }

  .summary-grid {
    gap: 12px;
  }

  .summary-value {
    font-size: 20px;
  }

  .percentage-track {
    display: none;
  }
}
</style>
