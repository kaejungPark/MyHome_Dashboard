<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../api/client'
import {
  Wallet,
  Coins,
  PiggyBank,
  ChartPie,
  CalendarDays,
  CalendarCheck,
  Package,
} from '@lucide/vue' // 대시보드 요약 카드에 사용할 아이콘

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
  items: DashboardItem[] // 물품 목록
}

// 화면에서 사용하는 데이터와 처리 상태다.
const dashboard = ref<DashboardResponse | null>(null)
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

// 날짜 문자열을 UTC 기준으로 변환해 시간대·서머타임에 따른 차이를 방지한다.
function toUtcDate(dateText: string): number {
  const [year, month, day] = dateText.split('-').map(Number)

  const date = new Date(0)
  date.setUTCFullYear(year!, month! - 1, day!)
  date.setUTCHours(0, 0, 0, 0)

  return date.getTime()
}

// 기준 날짜와 대상 날짜를 비교해 D-day를 표시한다.
function formatDday(targetDate: string): string {
  if (!dashboard.value) return '—'

  const days = Math.round(
    (toUtcDate(targetDate) - toUtcDate(dashboard.value.today)) / (1000 * 60 * 60 * 24),
  )

  if (days === 0) return 'D-day'
  return days > 0 ? `D-${days}` : `D+${Math.abs(days)}`
}

// 대시보드 요약 정보를 조회해 화면 데이터에 저장한다.
async function findDashboard() {
  const data = await request<DashboardResponse>('/api/dashboard')

  if (!data) throw new Error('조회 결과가 없습니다.')

  dashboard.value = data
}

async function loadData() {
  if (loading.value) return

  errorMessage.value = ''
  dashboard.value = null

  loading.value = true

  try {
    await findDashboard()
  } catch (error: unknown) {
    showError(error)
  } finally {
    // 성공·실패 여부와 관계없이 조회 상태를 해제한다.
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <main class="dashboard-page">
    <header class="page-header">
      <h1>대시보드</h1>
      <p>우리 집 생활 정보를 한눈에 확인하세요.</p>
    </header>

    <p v-if="loading" role="status">대시보드를 불러오는 중입니다.</p>
    <p v-else-if="errorMessage" class="error-message" role="alert">
      {{ errorMessage }}
    </p>

    <!--이번 달 생활비-->
    <section v-else-if="dashboard" class="expense-section" aria-labelledby="expense-heading">
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

    <!--이번 달 고정 지출 예정-->
    <section
      v-if="!loading && !errorMessage && dashboard"
      class="recurring-section"
      aria-labelledby="recurring-heading"
    >
      <div class="section-header">
        <div class="section-title">
          <div class="card-icon heading-icon" aria-hidden="true">
            <CalendarDays :size="26" />
          </div>
          <h2 id="recurring-heading">이번 달 고정 지출 예정</h2>
        </div>

        <RouterLink to="/recurring-expenses" class="more-link"> 더 보기 &rsaquo; </RouterLink>
      </div>

      <p class="amount">{{ formatAmount(dashboard.recurringAmount) }}<span>원</span></p>

      <p class="recurring-note">실제 지출과 별도로 계산한 예정 금액입니다.</p>
    </section>
    <div v-if="!loading && !errorMessage && dashboard" class="detail-grid">
      <section class="detail-section" aria-labelledby="schedule-heading">
        <div class="section-header">
          <div class="section-title">
            <CalendarCheck :size="24" class="section-icon" aria-hidden="true" />
            <h2 id="schedule-heading">마감이 가까운 일정</h2>
          </div>

          <RouterLink to="/schedules" class="more-link"> 더 보기 &rsaquo; </RouterLink>
        </div>

        <p class="detail-description">오늘부터 30일 이내 · 최대 5개</p>

        <p v-if="dashboard.schedules.length === 0" class="empty-message">
          마감이 가까운 미완료 일정이 없습니다.
        </p>

        <ul v-else class="detail-list">
          <li v-for="schedule in dashboard.schedules" :key="schedule.id" class="detail-row">
            <div class="detail-info">
              <span class="detail-name">{{ schedule.title }}</span>
              <time :datetime="schedule.dueDate"> 마감일 {{ schedule.dueDate }} </time>
            </div>

            <span class="dday-badge" :class="{ 'is-today': schedule.dueDate === dashboard.today }">
              {{ formatDday(schedule.dueDate) }}
            </span>
          </li>
        </ul>
      </section>

      <section class="detail-section" aria-labelledby="item-heading">
        <div class="section-header">
          <div class="section-title">
            <Package :size="24" class="section-icon" aria-hidden="true" />
            <h2 id="item-heading">보증 종료 예정 물품</h2>
          </div>

          <RouterLink to="/items" class="more-link"> 더 보기 &rsaquo; </RouterLink>
        </div>

        <p class="detail-description">오늘부터 30일 이내 · 최대 5개</p>

        <p v-if="dashboard.items.length === 0" class="empty-message">
          보증 종료가 가까운 물품이 없습니다.
        </p>

        <ul v-else class="detail-list">
          <li v-for="item in dashboard.items" :key="item.id" class="detail-row">
            <div class="detail-info">
              <span class="detail-name">{{ item.name }}</span>
              <time :datetime="item.warrantyEndDate"> 보증 종료일 {{ item.warrantyEndDate }} </time>
            </div>

            <span
              class="dday-badge"
              :class="{ 'is-today': item.warrantyEndDate === dashboard.today }"
            >
              {{ formatDday(item.warrantyEndDate) }}
            </span>
          </li>
        </ul>
      </section>
    </div>
  </main>
</template>

<style scoped>
.dashboard-page {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 28px 24px 48px;
  color: #1e293b;
}

.dashboard-page,
.dashboard-page * {
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 28px;
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
  .dashboard-page {
    padding: 20px 12px 32px;
  }

  .expense-section {
    padding: 16px;
  }

  .summary-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .page-header h1 {
    font-size: 24px;
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
  align-items: start;
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
</style>
