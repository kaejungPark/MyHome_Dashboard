<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  LayoutDashboard,
  Wallet,
  Coins,
  PiggyBank,
  ChartPie,
  CalendarDays,
  CalendarCheck,
  Carrot,
  ListChecks,
  BellRing,
} from '@lucide/vue' // 대시보드 요약 카드에 사용할 아이콘
import { requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { useErrorMessage } from '../composables/useErrorMessage'

// 대시보드에 표시할 일정 요약
interface DashboardSchedule {
  id: number
  title: string
  scheduleType: 'GENERAL' | 'PAYMENT' | 'RENEWAL'
  dueDate: string
}

// 대시보드에 표시할 물품 요약
interface DashboardItem {
  id: number
  name: string
  warrantyEndDate: string
}

// 납부일이 가까운 미납 고정 지출의 요약 정보다.
interface DashboardPayment {
  id: number
  title: string
  amount: number
  paymentDate: string
}

// 소비기한 확인 목록에 표시할 식재료 요약 정보다.
interface DashboardIngredient {
  id: number
  name: string
  storageLocation: 'REFRIGERATOR' | 'FREEZER' | 'PANTRY'
  expirationDate: string
}

// 다음 예정일을 안내할 집안일·소모품의 요약 정보다.
interface DashboardHouseholdTask {
  id: number
  name: string
  taskType: 'CHORE' | 'CONSUMABLE'
  nextDueDate: string
}

// 대시보드 API 응답 데이터
interface DashboardResponse {
  today: string // 조회 기준 날짜
  month: string // 조회 월
  totalAmount: number // 실제 지출 합계
  configured: boolean // 예산 등록 여부
  budgetAmount: number | null // 월 예산
  remainingAmount: number | null // 잔여 예산
  usageRate: number | null // 예산 사용률
  recurringAmount: number // 고정 지출 예정 합계
  schedules: DashboardSchedule[] // 일정 목록
  scheduleCount: number //대상 일정 전체 건수
  items: DashboardItem[] // 물품 목록
  payments: DashboardPayment[] // 조회 기준일부터 7일 뒤까지 납부 예정인 미납 항목
  paymentCount: number // 대상 미납 전체 건수
  ingredients: DashboardIngredient[] // 식재료 목록
  ingredientCount: number // 식재료 전체 건수
  householdTasks: DashboardHouseholdTask[] // 집안일·소모품 목록
  householdTaskCount: number // 집안일·소모품 전체 건수
}

// 화면에서 사용하는 데이터와 처리 상태다.
const dashboard = ref<DashboardResponse | null>(null)
const loading = ref(false)

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)

const { errorMessage, showError } = useErrorMessage('통계 조회 중 오류가 발생했습니다.') // 화면의 오류 메시지와 공통 오류 처리 함수를 사용한다.

// 금액을 천 단위 쉼표로 표시한다.
function formatAmount(amount: number | null) {
  return amount === null ? '—' : amount.toLocaleString('ko-KR')
}

/**
 * YYYY-MM-DD 날짜를 일수 계산에 사용할 숫자로 변환한다.
 * 두 날짜를 모두 UTC 자정 기준으로 맞춰 브라우저 시간대의 영향을 줄인다.
 * 화면의 날짜를 UTC 날짜로 바꾸려는 용도는 아니다.
 */
function toUtcDate(dateText: string): number {
  const [year, month, day] = dateText.split('-').map(Number)

  const date = new Date(0)
  date.setUTCFullYear(year!, month! - 1, day!)
  date.setUTCHours(0, 0, 0, 0)

  return date.getTime()
}

/**
 * 조회 대상 날짜와 서버가 반환한 기준 날짜의 차이를 표시한다.
 * 오늘은 D-day, 남은 날짜는 D-N, 지난 날짜는 D+N으로 표시한다.
 * PC의 오늘 날짜 대신 응답의 today를 사용해 조회 기준과 맞춘다.
 */
function formatDday(targetDate: string): string {
  if (!dashboard.value) return '—'

  const days = Math.round(
    (toUtcDate(targetDate) - toUtcDate(dashboard.value.today)) / (1000 * 60 * 60 * 24),
  )

  if (days === 0) return 'D-day'
  return days > 0 ? `D-${days}` : `D+${Math.abs(days)}`
}

/**
 * 공통 요청 함수를 통해 대시보드 정보를 가져온다.
 * 받은 데이터는 요약 카드와 목록에 적용하고,
 * 체험 데이터 여부는 화면의 안내 문구 표시에 사용한다.
 */
async function findDashboard() {
  const result = await requestWithDemo<DashboardResponse>(
    '/api/dashboard',
    () => createDemoData().dashboard,
  )

  dashboard.value = result.data
  isDemo.value = result.isDemo
}

/**
 * 기존 조회 결과를 초기화하고 대시보드를 다시 불러온다.
 * 중복 조회를 막고, 실패하면 공통 오류 메시지를 표시한다.
 * 성공 여부와 관계없이 마지막에 로딩 상태를 해제한다.
 */
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  dashboard.value = null
  isDemo.value = false

  try {
    await findDashboard()
  } catch (error: unknown) {
    // 서버·통신 오류를 샘플 데이터로 숨기지 않는다.
    showError(error)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <main class="dashboard-page">
    <header class="crud-page page-heading">
      <div class="page-icon">
        <LayoutDashboard :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>대시보드</h1>
        <p>우리 집 생활 정보를 한눈에 확인하세요.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험 중이에요. 현재 정보는 샘플 데이터입니다. 로그인하면 내 정보를 관리할 수 있어요.
    </p>

    <p v-if="loading" role="status">대시보드를 불러오는 중입니다.</p>

    <p v-else-if="errorMessage" class="error-message" role="alert">
      {{ errorMessage }}
    </p>

    <!-- 응답 데이터가 있을 때만 내부 값을 읽어 요약 카드와 목록을 표시한다. -->
    <template v-else-if="dashboard">
      <!-- 상단: 이번 달 생활비·예산 요약 -->
      <section class="expense-section" aria-labelledby="expense-heading">
        <div class="section-header">
          <div class="section-title">
            <h2 id="expense-heading">이번 달 생활비</h2>
            <span class="month-badge">{{ dashboard.month }}</span>
          </div>

          <RouterLink to="/statistics" class="more-link"> 더 보기 &rsaquo; </RouterLink>
        </div>

        <div class="summary-grid">
          <article class="summary-card">
            <div class="card-icon" aria-hidden="true">
              <Wallet :size="26" />
            </div>
            <h3>이번 달 지출</h3>
            <p class="amount">{{ formatAmount(dashboard.totalAmount) }}<span>원</span></p>
          </article>

          <article class="summary-card">
            <div class="card-icon icon-slate" aria-hidden="true">
              <Coins :size="26" />
            </div>
            <h3>월 예산</h3>
            <p class="amount">
              {{ dashboard.configured ? formatAmount(dashboard.budgetAmount) : '미등록' }}
              <span v-if="dashboard.configured">원</span>
            </p>
          </article>

          <article class="summary-card">
            <div class="card-icon icon-green" aria-hidden="true">
              <PiggyBank :size="26" />
            </div>
            <h3>잔여 예산</h3>
            <p
              class="amount"
              :class="{
                remaining: dashboard.remainingAmount !== null && dashboard.remainingAmount >= 0,
                'over-budget': dashboard.remainingAmount !== null && dashboard.remainingAmount < 0,
              }"
            >
              {{ formatAmount(dashboard.remainingAmount) }}
              <span v-if="dashboard.remainingAmount !== null">원</span>
            </p>
          </article>

          <article class="summary-card">
            <div class="card-icon" aria-hidden="true">
              <ChartPie :size="26" />
            </div>
            <h3>예산 사용률</h3>
            <p
              class="amount"
              :class="{
                'over-budget': dashboard.usageRate !== null && dashboard.usageRate > 100,
              }"
            >
              {{ dashboard.usageRate?.toFixed(2) ?? '—' }}
              <span v-if="dashboard.usageRate !== null">%</span>
            </p>

            <progress
              v-if="dashboard.usageRate !== null"
              class="usage-progress"
              :value="Math.min(100, Math.max(0, dashboard.usageRate))"
              max="100"
              aria-label="예산 사용률"
            >
              {{ dashboard.usageRate }}%
            </progress>

            <p v-if="dashboard.configured && dashboard.budgetAmount === 0" class="card-note">
              예산이 0원이므로 사용률을 계산하지 않습니다.
            </p>
          </article>
        </div>
      </section>

      <!-- 두 번째 줄: 고정 지출 / 일정 -->
      <div class="detail-grid">
        <section class="detail-section" aria-labelledby="recurring-heading">
          <div class="section-header">
            <div class="section-title">
              <CalendarDays :size="24" class="section-icon" aria-hidden="true" />
              <h2 id="recurring-heading">고정 지출</h2>
            </div>

            <RouterLink to="/recurring-expenses" class="more-link"> 더 보기 &rsaquo; </RouterLink>
          </div>

          <p class="detail-description">이번 달 고정 지출 예정 합계</p>
          <p class="amount">{{ formatAmount(dashboard.recurringAmount) }}<span>원</span></p>
          <p class="recurring-note">실제 지출과 별도로 계산한 예정 금액입니다.</p>

          <div class="section-header">
            <div class="section-title">
              <BellRing :size="20" aria-hidden="true" />
              <h3>납부일이 가까운 미납 항목</h3>
              <span class="count-badge">{{ dashboard.paymentCount }}건</span>
            </div>
          </div>

          <p class="detail-description">오늘부터 7일 이내 · 최대 3건</p>

          <p v-if="dashboard.payments.length === 0" class="empty-message">
            7일 이내 납부할 미납 항목이 없습니다.
          </p>

          <ul v-else class="detail-list">
            <li v-for="payment in dashboard.payments" :key="`${payment.id}-${payment.paymentDate}`">
              <RouterLink to="/recurring-expenses" class="detail-row payment-link">
                <div class="detail-info">
                  <span class="detail-name">{{ payment.title }}</span>
                  <span class="payment-amount"> {{ formatAmount(payment.amount) }}원 </span>
                  <time :datetime="payment.paymentDate">
                    납부 예정일 {{ payment.paymentDate }}
                  </time>
                </div>

                <span
                  class="dday-badge"
                  :class="{
                    'is-today': payment.paymentDate === dashboard.today,
                  }"
                >
                  {{ formatDday(payment.paymentDate) }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </section>

        <section class="detail-section" aria-labelledby="schedule-heading">
          <div class="section-header">
            <div class="section-title">
              <CalendarCheck :size="24" class="section-icon" aria-hidden="true" />
              <h2 id="schedule-heading">확인할 일정</h2>
              <span class="count-badge">{{ dashboard.scheduleCount }}건</span>
            </div>

            <RouterLink to="/schedules" class="more-link"> 더 보기 &rsaquo; </RouterLink>
          </div>

          <p class="detail-description">기한 경과 포함 · 7일 이내 마감 · 미완료 · 최대 3건</p>

          <p v-if="dashboard.schedules.length === 0" class="empty-message">
            확인할 미완료 일정이 없습니다.
          </p>

          <ul v-else class="detail-list">
            <li v-for="schedule in dashboard.schedules" :key="schedule.id">
              <RouterLink to="/schedules" class="detail-row payment-link">
                <div class="detail-info">
                  <span class="detail-name">{{ schedule.title }}</span>
                  <time :datetime="schedule.dueDate"> 마감일 {{ schedule.dueDate }} </time>
                </div>

                <span
                  class="dday-badge"
                  :class="{
                    'is-overdue': schedule.dueDate < dashboard.today,
                    'is-today': schedule.dueDate === dashboard.today,
                  }"
                >
                  {{
                    schedule.dueDate < dashboard.today
                      ? '기한 지남'
                      : schedule.dueDate === dashboard.today
                        ? '오늘 마감'
                        : formatDday(schedule.dueDate)
                  }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </section>
      </div>

      <!-- 세 번째 줄: 식재료 / 집안일·소모품 -->
      <div class="detail-grid">
        <section class="detail-section" aria-labelledby="ingredient-heading">
          <div class="section-header">
            <div class="section-title">
              <Carrot :size="24" class="section-icon" aria-hidden="true" />
              <h2 id="ingredient-heading">소비기한 확인</h2>
              <span class="count-badge"> {{ dashboard.ingredientCount }}건 </span>
            </div>

            <RouterLink to="/ingredients" class="more-link"> 더 보기 &rsaquo; </RouterLink>
          </div>

          <p class="detail-description">기한 경과 포함 · 3일 이내 · 보관 중 · 최대 3건</p>

          <p v-if="dashboard.ingredients.length === 0" class="empty-message">
            소비기한 확인이 필요한 식재료가 없습니다.
          </p>

          <ul v-else class="detail-list">
            <li v-for="ingredient in dashboard.ingredients" :key="ingredient.id">
              <RouterLink to="/ingredients" class="detail-row payment-link">
                <div class="detail-info">
                  <span class="detail-name">{{ ingredient.name }}</span>
                  <span>
                    {{
                      ingredient.storageLocation === 'REFRIGERATOR'
                        ? '냉장'
                        : ingredient.storageLocation === 'FREEZER'
                          ? '냉동'
                          : '실온'
                    }}
                  </span>
                  <time :datetime="ingredient.expirationDate">
                    소비기한 {{ ingredient.expirationDate }}
                  </time>
                </div>

                <span
                  class="dday-badge"
                  :class="{
                    'is-overdue': ingredient.expirationDate < dashboard.today,
                    'is-today': ingredient.expirationDate === dashboard.today,
                  }"
                >
                  {{
                    ingredient.expirationDate < dashboard.today
                      ? '소비기한 경과'
                      : ingredient.expirationDate === dashboard.today
                        ? '오늘까지'
                        : formatDday(ingredient.expirationDate)
                  }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </section>

        <section class="detail-section" aria-labelledby="household-heading">
          <div class="section-header">
            <div class="section-title">
              <ListChecks :size="24" class="section-icon" aria-hidden="true" />
              <h2 id="household-heading">집안일·소모품</h2>
              <span class="count-badge"> {{ dashboard.householdTaskCount }}건 </span>
            </div>

            <RouterLink to="/household-tasks" class="more-link"> 더 보기 &rsaquo; </RouterLink>
          </div>

          <p class="detail-description">예정일 경과 포함 · 7일 이내 · 사용 중 · 최대 3건</p>

          <p v-if="dashboard.householdTasks.length === 0" class="empty-message">
            예정된 집안일이나 소모품 교체 항목이 없습니다.
          </p>

          <ul v-else class="detail-list">
            <li v-for="task in dashboard.householdTasks" :key="task.id">
              <RouterLink to="/household-tasks" class="detail-row payment-link">
                <div class="detail-info">
                  <span class="detail-name">{{ task.name }}</span>
                  <span>
                    {{ task.taskType === 'CHORE' ? '집안일' : '소모품' }}
                  </span>
                  <time :datetime="task.nextDueDate"> 다음 예정일 {{ task.nextDueDate }} </time>
                </div>

                <span
                  class="dday-badge"
                  :class="{
                    'is-overdue': task.nextDueDate < dashboard.today,
                    'is-today': task.nextDueDate === dashboard.today,
                  }"
                >
                  {{
                    task.nextDueDate < dashboard.today
                      ? '예정일 지남'
                      : task.nextDueDate === dashboard.today
                        ? '오늘 예정'
                        : formatDday(task.nextDueDate)
                  }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.crud-page .page-heading,
.crud-page.page-heading {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.crud-page .page-heading h1,
.crud-page.page-heading h1 {
  margin: 0 0 4px;
  font-size: 28px;
}

.crud-page .page-heading p,
.crud-page.page-heading p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.dashboard-page {
  width: 100%;
  min-width: 0;
  color: #1e293b;
}

.dashboard-page,
.dashboard-page * {
  box-sizing: border-box;
}

.expense-section {
  padding: 24px;
  border: 1px solid #dce3ed;
  border-radius: 12px;
  background: #fff;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.section-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.month-badge {
  padding: 5px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 13px;
}

.more-link {
  color: #2563eb;
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.more-link:hover {
  text-decoration: underline;
}

.more-link:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 4px;
  border-radius: 3px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  min-width: 0;
  padding: 22px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.summary-card h3 {
  margin: 0 0 18px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}

.amount {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.4;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.amount span {
  margin-left: 3px;
  font-size: 16px;
  font-weight: 500;
}

.remaining {
  color: #047857;
}

.usage-progress {
  display: block;
  width: 100%;
  height: 8px;
  margin-top: 14px;
  overflow: hidden;
  appearance: none;
  border: none;
  border-radius: 999px;
  background: #e2e8f0;
}

.usage-progress::-webkit-progress-bar {
  border-radius: 999px;
  background: #e2e8f0;
}

.usage-progress::-webkit-progress-value {
  border-radius: 999px;
  background: #2563eb;
}

.usage-progress::-moz-progress-bar {
  border-radius: 999px;
  background: #2563eb;
}

.over-budget,
.error-message {
  color: #dc2626;
}

.card-note {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.recurring-section {
  margin-top: 24px;
  padding: 24px;
  border: 1px solid #dce3ed;
  border-radius: 12px;
  background: #fff;
}

.recurring-section h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.recurring-note {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 560px) {
  .recurring-section {
    padding: 16px;
  }
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .expense-section {
    padding: 16px;
  }

  .summary-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  margin-bottom: 16px;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
}

.icon-slate {
  background: #f1f5f9;
  color: #64748b;
}

.icon-green {
  background: #ecfdf5;
  color: #059669;
}

.heading-icon {
  margin-bottom: 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch; /* start에서 변경 */
  gap: 20px;
  margin-top: 24px;
}

.detail-section {
  min-width: 0;
  padding: 24px;
  border: 1px solid #dce3ed;
  border-radius: 12px;
  background: #fff;
}

.detail-section .section-header {
  margin-bottom: 10px;
}

.detail-section h2 {
  font-size: 18px;
}

.section-icon {
  flex-shrink: 0;
  color: #2563eb;
}

.detail-description {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.detail-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #e2e8f0;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.detail-name {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.detail-info time {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.dday-badge {
  flex-shrink: 0;
  min-width: 64px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.dday-badge.is-today {
  background: #fef2f2;
  color: #dc2626;
}

.empty-message {
  margin: 0;
  padding: 28px 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
  text-align: center;
}

.demo-notice {
  margin: 0 0 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #eaf1ff;
  color: #2563eb;
  font-size: 14px;
}

.payment-link {
  color: inherit;
  text-decoration: none;
}

.payment-link:hover {
  background-color: #f8fafc;
}

.payment-link:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
  border-radius: 6px;
}

/* 각 li 안에 링크가 하나씩 있으므로 기존 마지막 행 규칙을 보정한다. */
.detail-list > li:not(:last-child) > .payment-link {
  border-bottom: 1px solid #e2e8f0;
}

.payment-amount {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

/* 기한이 지난 항목을 구분한다. */
.dday-badge.is-overdue {
  background-color: #fef2f2;
  color: #b91c1c;
}

/* 긴 상태 문구가 줄바꿈되거나 좁아지는 것을 막는다. */
.dday-badge {
  flex-shrink: 0;
  white-space: nowrap;
}

.section-title h3 {
  margin: 0;
  font-size: 16px;
}

@media (max-width: 640px) {
  .crud-page .page-heading h1,
  .crud-page.page-heading h1 {
    font-size: 24px;
  }
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .detail-section {
    padding: 16px;
  }
}

/* 좁은 화면에서는 요약 영역을 한 열로 배치한다. */
@media (max-width: 768px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
