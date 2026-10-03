<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { ChartPie, Search } from '@lucide/vue'

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

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)

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
  statistics.value = null
  isDemo.value = false

  const result = await requestWithDemo<Statistics>(
    `/api/statistics/${selectedMonth.value}`,
    () => createDemoData(selectedMonth.value).statistics,
  )

  statistics.value = result.data

  // 관리 목록 또는 월별 목록이 샘플이면 체험 상태를 유지한다.
  isDemo.value = result.isDemo
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

// 화면이 처음 표시되면 현재 월의 통계를 조회한다.
onMounted(loadStatistics)
</script>
<template>
  <section class="crud-page statistics-page">
    <header class="page-heading">
      <div class="page-icon">
        <ChartPie :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>월별 통계</h1>
        <p>월별 지출과 예산 사용 현황을 확인합니다.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험 중이에요. 현재 정보는 샘플 데이터입니다. 로그인하면 내 정보를 관리할 수 있어요.
    </p>

    <section class="card">
      <form class="month-form" @submit.prevent="loadStatistics">
        <div class="form-grid statistics-filter">
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
        </div>

        <button type="submit" class="btn btn-primary" :disabled="loading">
          <Search :size="16" aria-hidden="true" />
          {{ loading ? '조회 중…' : '조회' }}
        </button>
      </form>
    </section>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <p v-if="loading" class="empty-state" role="status">통계를 불러오는 중입니다.</p>

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
        <div class="card-heading">
          <div class="heading-title">
            <ChartPie :size="20" aria-hidden="true" />
            <h2>카테고리별 지출</h2>
          </div>
          <span class="count-badge"> {{ statistics.categories.length }}개 카테고리 </span>
        </div>

        <p v-if="statistics.categories.length === 0" class="empty-state">
          해당 월에 등록된 지출이 없습니다.
        </p>

        <div v-else class="table-wrap">
          <table class="data-table statistics-table">
            <caption class="sr-only">
              {{
                statistics.month
              }}
              카테고리별 지출 금액과 비율
            </caption>
            <thead>
              <tr>
                <th scope="col">카테고리</th>
                <th scope="col" class="amount-cell">지출 금액</th>
                <th scope="col" class="percentage-column">비율</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="category in statistics.categories" :key="category.categoryId">
                <td>{{ category.categoryName }}</td>
                <td class="amount-cell">{{ formatAmount(category.amount) }}원</td>
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
                <td class="amount-cell">{{ formatAmount(statistics.totalAmount) }}원</td>
                <td></td>
              </tr>
            </tfoot>
          </table>

          <p class="table-note">
            비율은 소수점 둘째 자리까지 표시하며, 반올림으로 합계가 100%와 다를 수 있습니다.
          </p>
        </div>
      </section>
    </template>

    <p v-else-if="!errorMessage" class="empty-state">조회할 월을 선택해 주세요.</p>
  </section>
</template>

<style scoped>
/* 월 선택과 조회 버튼 */
.month-form {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}

.statistics-page .statistics-filter {
  flex: 0 1 240px;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
}

.month-form .btn {
  min-height: 44px;
}

.result-title {
  margin: 28px 0 16px;
  font-size: 20px;
}

/* 월별 요약 카드 */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.statistics-page .summary-card {
  min-width: 0;
  margin-bottom: 0;
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

.statistics-page .over-budget {
  color: #dc2626;
}

/* 예산 미등록 안내 */
.info-message {
  margin: 16px 0 0;
  padding: 14px 16px;
  border-radius: 8px;
  background-color: #eff6ff;
  color: #1e40af;
  font-size: 14px;
  line-height: 1.6;
}

.info-message a {
  margin-left: 8px;
  color: #1d4ed8;
  text-decoration: underline;
}

.info-message a:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 3px;
}

/* 카테고리별 통계 */
.category-section {
  margin-top: 24px;
}

.statistics-table {
  min-width: 520px;
}

.statistics-page .data-table .percentage-column {
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
  background-color: #e2e8f0;
}

.percentage-fill {
  height: 100%;
  border-radius: inherit;
  background-color: #2563eb;
}

.percentage-cell span {
  min-width: 68px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 합계 행 */
.statistics-page .data-table tfoot th,
.statistics-page .data-table tfoot td {
  border-bottom: none;
  background-color: #f8fafc;
  font-weight: 700;
}

.table-note {
  margin: 16px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.demo-notice {
  margin: 0 0 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #eaf1ff;
  color: #2563eb;
  font-size: 14px;
}

/* 화면 너비에 따라 요약 카드 개수를 조정한다. */
@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .summary-grid {
    grid-template-columns: 1fr;
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
