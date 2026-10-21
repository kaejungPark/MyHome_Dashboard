<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { request } from '../api/client'
import { ShieldCheck, Users, UserPlus, Flag, EyeOff } from '@lucide/vue'

// 관리자 대시보드의 기본 통계 응답이다.
interface AdminDashboardResponse {
  totalUserCount: number
  todaySignupCount: number
  pendingReportCount: number
  hiddenCommunityCount: number
}

// 통계가 조회되기 전에는 null을 유지하여 실제 0건과 구분한다.
const dashboard = ref<AdminDashboardResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')

// 한국 날짜 기준의 날짜별 가입자 수다.
interface AdminSignupTrendResponse {
  date: string
  signupCount: number
}

const signupTrend = ref<AdminSignupTrendResponse[]>([])

// 최근 7일 가입자 수의 합계를 계산한다.
const totalSignupCount = computed(() =>
  signupTrend.value.reduce((total, item) => total + item.signupCount, 0),
)

// 모든 날짜가 0명인 경우에도 나눗셈이 가능하도록 최솟값을 1로 둔다.
const maxSignupCount = computed(() =>
  Math.max(1, ...signupTrend.value.map((item) => item.signupCount)),
)

// 서버에서 받은 날짜 문자열을 시간대 변환 없이 월/일로 표시한다.
function signupDateLabel(date: string): string {
  return `${date.slice(5, 7)}/${date.slice(8, 10)}`
}

/**
 * 관리자 기본 통계와 최근 7일 가입 추이를 함께 조회한다.
 * 두 요청이 모두 성공한 경우에만 화면 데이터를 반영한다.
 */
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  dashboard.value = null
  signupTrend.value = []

  try {
    const [dashboardResult, trendResult] = await Promise.all([
      request<AdminDashboardResponse>('/api/admin/dashboard'),
      request<AdminSignupTrendResponse[]>('/api/admin/dashboard/signup-trend'),
    ])

    if (dashboardResult === null || trendResult === null) {
      throw new Error('관리자 통계 정보를 받지 못했습니다.')
    }

    dashboard.value = dashboardResult
    signupTrend.value = trendResult
  } catch (error: unknown) {
    errorMessage.value =
      error instanceof Error ? error.message : '관리자 통계를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

// 관리자 화면에 진입하면 기본 통계를 조회한다.
onMounted(loadData)
</script>

<template>
  <section class="crud-page admin-page" :aria-busy="loading">
    <!-- 기존 관리 화면과 동일한 제목 및 아이콘 배치를 사용한다. -->
    <header class="page-heading">
      <div class="page-icon">
        <ShieldCheck :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>관리자 대시보드</h1>
        <p>회원 현황과 커뮤니티 관리 현황을 확인합니다.</p>
      </div>
    </header>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <p v-if="loading" class="admin-loading" role="status">관리자 통계를 불러오는 중입니다.</p>

    <!-- 조회 중이거나 조회에 실패한 경우 이전 통계를 표시하지 않는다. -->
    <div v-else-if="!errorMessage" class="admin-summary-grid">
      <article class="card admin-stat-card">
        <div class="admin-stat-heading">
          <h2>전체 회원</h2>
          <span class="admin-stat-icon icon-blue">
            <Users :size="22" aria-hidden="true" />
          </span>
        </div>
        <p class="admin-stat-value">
          {{ dashboard?.totalUserCount.toLocaleString() ?? '—' }}
          <span class="admin-stat-unit">명</span>
        </p>
        <p class="admin-stat-description">탈퇴 회원을 제외한 전체 회원</p>
      </article>

      <article class="card admin-stat-card">
        <div class="admin-stat-heading">
          <h2>오늘 가입</h2>
          <span class="admin-stat-icon icon-green">
            <UserPlus :size="22" aria-hidden="true" />
          </span>
        </div>
        <p class="admin-stat-value">
          {{ dashboard?.todaySignupCount.toLocaleString() ?? '—' }}
          <span class="admin-stat-unit">명</span>
        </p>
        <p class="admin-stat-description">한국 시간 기준 오늘 가입한 회원</p>
      </article>

      <article class="card admin-stat-card">
        <div class="admin-stat-heading">
          <h2>처리 대기 신고</h2>
          <span class="admin-stat-icon icon-red">
            <Flag :size="22" aria-hidden="true" />
          </span>
        </div>
        <p class="admin-stat-value">
          {{ dashboard?.pendingReportCount.toLocaleString() ?? '—' }}
          <span class="admin-stat-unit">건</span>
        </p>
        <p class="admin-stat-description">관리자 확인과 처리가 필요한 신고</p>
      </article>

      <article class="card admin-stat-card">
        <div class="admin-stat-heading">
          <h2>숨김 게시글</h2>
          <span class="admin-stat-icon icon-slate">
            <EyeOff :size="22" aria-hidden="true" />
          </span>
        </div>
        <p class="admin-stat-value">
          {{ dashboard?.hiddenCommunityCount.toLocaleString() ?? '—' }}
          <span class="admin-stat-unit">건</span>
        </p>
        <p class="admin-stat-description">현재 숨김 처리된 커뮤니티 게시글</p>
      </article>
    </div>

    <!-- 기본 통계 카드 아래에 최근 7일 가입 추이를 표시한다. -->
    <section
      v-if="!loading && !errorMessage && dashboard"
      class="card signup-trend-card"
      aria-labelledby="signup-trend-title"
    >
      <div class="card-heading">
        <div class="heading-title">
          <UserPlus :size="20" aria-hidden="true" />
          <h2 id="signup-trend-title">최근 7일 가입 추이</h2>
        </div>

        <span class="heading-note">
          총 {{ totalSignupCount.toLocaleString() }}명 · 한국 시간 기준
        </span>
      </div>

      <p v-if="signupTrend.length === 0" class="trend-empty">가입 추이 정보가 없습니다.</p>

      <template v-else>
        <p v-if="totalSignupCount === 0" class="trend-note">
          최근 7일 동안 가입한 회원이 없습니다.
        </p>

        <!-- 날짜별 인원수를 텍스트로도 제공하여 색상 없이 확인할 수 있다. -->
        <ol class="signup-chart" aria-label="최근 7일 날짜별 가입자 수">
          <li v-for="item in signupTrend" :key="item.date" class="signup-chart-column">
            <span class="signup-chart-count"> {{ item.signupCount.toLocaleString() }}명 </span>

            <div class="signup-chart-track" aria-hidden="true">
              <div
                class="signup-chart-bar"
                :style="{
                  height: `${(item.signupCount / maxSignupCount) * 100}%`,
                }"
              ></div>
            </div>

            <time :datetime="item.date" class="signup-chart-date">
              {{ signupDateLabel(item.date) }}
            </time>
          </li>
        </ol>
      </template>
    </section>
  </section>
</template>

<style scoped>
/* 기본 제목과 카드 모양은 common.css를 사용한다. */
.admin-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

/* 공통 카드의 아래 여백 대신 그리드 간격으로 배치를 조절한다. */
.admin-page .admin-stat-card {
  min-width: 0;
  margin-bottom: 0;
}

.admin-stat-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.admin-stat-heading h2 {
  margin: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
}

.admin-stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 12px;
}

.icon-blue {
  background-color: #eaf1ff;
  color: #2563eb;
}

.icon-green {
  background-color: #ecfdf5;
  color: #059669;
}

.icon-red {
  background-color: #fef2f2;
  color: #dc2626;
}

.icon-slate {
  background-color: #f1f5f9;
  color: #64748b;
}

.admin-stat-value {
  margin: 0 0 12px;
  color: #1e293b;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.admin-stat-unit {
  margin-left: 4px;
  color: #64748b;
  font-size: 14px;
  font-weight: 400;
}

.admin-stat-description {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.admin-loading {
  margin: 0;
  padding: 40px 16px;
  color: #64748b;
  font-size: 14px;
  text-align: center;
}

/* 기본 통계 카드와 가입 추이 영역 사이에 간격을 둔다. */
.admin-page .signup-trend-card {
  margin-top: 24px;
}

.signup-chart {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
  padding: 8px 0 0;
  list-style: none;
}

.signup-chart-column {
  min-width: 0;
  text-align: center;
}

.signup-chart-count {
  display: block;
  margin-bottom: 10px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.signup-chart-track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 180px;
  border-bottom: 1px solid #dce3ed;
}

.signup-chart-bar {
  width: 100%;
  max-width: 48px;
  border-radius: 6px 6px 0 0;
  background-color: #2563eb;
}

.signup-chart-date {
  display: block;
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.trend-note,
.trend-empty {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.trend-empty {
  padding: 24px 0;
  text-align: center;
}

/* 중간 크기 화면에서는 두 개씩 배치한다. */
@media (max-width: 1100px) {
  .admin-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 모바일에서는 통계 카드를 한 개씩 배치한다. */
@media (max-width: 560px) {
  .admin-page .signup-trend-card {
    padding: 16px;
  }

  .signup-chart {
    gap: 6px;
  }

  .signup-chart-track {
    height: 140px;
  }

  .signup-chart-count,
  .signup-chart-date {
    font-size: 11px;
  }
}
</style>
