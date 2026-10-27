<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Flag, Search } from '@lucide/vue'
import { request } from '../api/client'
import type { PageResponse } from '../types/page'
import Pagination from '../components/Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { useErrorMessage } from '../composables/useErrorMessage'

// DB에 저장되는 신고 상태: 대기, 수용, 기각.
type ReportStatus = 'PENDING' | 'RESOLVED' | 'REJECTED'

// 검색에서는 모든 상태를 조회하는 ALL도 허용한다.
type SearchStatus = 'ALL' | ReportStatus

// 관리자가 처리할 때는 수용 또는 기각만 선택할 수 있다.
type ProcessStatus = 'RESOLVED' | 'REJECTED'

// 신고 목록의 한 항목이다.
interface AdminReport {
  id: number // 신고 ID
  communityId: number // 신고 대상 게시글 ID
  title: string // 신고 대상 게시글 제목
  reporterNickname: string // 신고한 사용자 닉네임
  reason: string // 신고 사유 코드. 화면에서는 한글 명칭으로 변환
  status: ReportStatus // 현재 신고 처리 상태
  createdDt: string // 신고 접수 시각(UTC)
}

// 목록의 기본 정보에 신고 설명과 처리 이력을 추가한 상세 정보다.
interface AdminReportDetail extends AdminReport {
  description: string | null // 신고자의 상세 설명
  content: string // 신고 대상 게시글 본문
  authorNickname: string // 게시글 작성자 닉네임
  isHidden: boolean // 게시글의 현재 숨김 여부
  processedBy: number | null // 처리한 관리자 ID
  processedDt: string | null // 처리 시각(UTC)
  processNote: string | null // 관리자가 남긴 처리 의견
}

const baseUrl = '/api/admin/community/reports'

// 목록 조회 상태
const reports = ref<AdminReport[]>([])
const loading = ref(false)
const { errorMessage, showError } = useErrorMessage('신고 목록을 불러오지 못했습니다.')

// 사용자가 선택창에서 고른 값이다. 아직 검색에 반영되지 않을 수 있다.
const searchStatus = ref<SearchStatus>('PENDING')

// 조회 버튼을 눌러 확정한 조건이다. 페이지 이동 시에도 이 값을 사용한다.
const appliedStatus = ref<SearchStatus>('PENDING')

const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(10)

// 상세 조회 상태
const selectedReportId = ref<number | null>(null)
const reportDetail = ref<AdminReportDetail | null>(null)
const detailLoading = ref(false)
const { errorMessage: detailError, showError: showDetailError } =
  useErrorMessage('신고 상세 정보를 불러오지 못했습니다.')

// 신고 처리 상태
const processNote = ref('')
const processing = ref(false)
const notice = ref('')
const { errorMessage: processError, showError: showProcessError } =
  useErrorMessage('신고 처리에 실패했습니다.')

// 다른 신고를 선택하거나 상세를 닫은 뒤 도착한 이전 응답을 무시한다.
let detailRequestSequence = 0

/**
 * 상세 카드와 입력 상태를 초기화한다.
 * 진행 중이던 상세 조회 응답도 무효화한다.
 */
function closeReportDetail() {
  if (processing.value) return

  detailRequestSequence++
  selectedReportId.value = null
  reportDetail.value = null
  detailLoading.value = false
  detailError.value = ''
  processNote.value = ''
  processError.value = ''
}

/**
 * 적용된 검색 조건으로 신고 목록을 조회한다.
 * 목록 변경 시 이전 신고 상세는 닫는다.
 */
async function loadData() {
  if (loading.value || processing.value) return

  closeReportDetail()

  loading.value = true
  errorMessage.value = ''
  reports.value = []
  resetTotals()

  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: String(size.value),
      status: appliedStatus.value,
    })

    const result = await request<PageResponse<AdminReport>>(`${baseUrl}?${params.toString()}`)

    if (result === null) {
      throw new Error('신고 목록 정보를 받지 못했습니다.')
    }

    reports.value = result.items
    applyPage(result)
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 조회 버튼을 누른 경우에만 선택한 상태를 검색 조건에 반영한다.
async function searchReports() {
  if (loading.value || processing.value) return

  notice.value = ''
  appliedStatus.value = searchStatus.value
  resetPage()
  await loadData()
}

// 현재 적용된 검색 조건을 유지하면서 페이지를 이동한다.
async function changePage(targetPage: number) {
  if (loading.value || processing.value || !canMoveTo(targetPage)) {
    return
  }

  notice.value = ''
  page.value = targetPage
  await loadData()
}

/**
 * 게시글 ID가 아닌 신고 ID로 상세 정보를 조회한다.
 * 가장 최근에 선택한 신고의 응답만 화면에 반영한다.
 */
async function openReportDetail(reportId: number) {
  if (loading.value || processing.value) return

  // 이번 요청 번호를 저장해 나중에 도착한 응답이 최신 요청인지 확인한다.
  const requestSequence = ++detailRequestSequence

  selectedReportId.value = reportId
  reportDetail.value = null
  detailError.value = ''
  detailLoading.value = true
  processNote.value = ''
  processError.value = ''
  notice.value = ''

  try {
    const result = await request<AdminReportDetail>(`${baseUrl}/${reportId}`)

    // 다른 신고를 선택했거나 상세를 닫았다면 이전 응답은 표시하지 않는다.
    // 서버 요청 자체를 취소하는 것은 아니다.
    if (requestSequence !== detailRequestSequence) return

    if (result === null) {
      throw new Error('신고 상세 정보를 받지 못했습니다.')
    }

    reportDetail.value = result
  } catch (error: unknown) {
    if (requestSequence !== detailRequestSequence) return

    showDetailError(error)
  } finally {
    // 이전 요청이 현재 요청의 로딩 표시를 끄지 않도록 최신 요청만 처리한다.
    if (requestSequence === detailRequestSequence) {
      detailLoading.value = false
    }
  }
}

/**
 * 처리 대기 신고를 수용하거나 기각한다.
 * 처리 성공 후 현재 검색 조건의 첫 페이지를 다시 조회한다.
 */
async function processReport(status: ProcessStatus) {
  if (processing.value || loading.value || detailLoading.value) return

  const detail = reportDetail.value
  if (!detail) return

  processError.value = ''
  notice.value = ''

  if (detail.status !== 'PENDING') {
    processError.value = '이미 처리된 신고입니다.'
    return
  }

  const note = processNote.value.trim()

  if (note.length > 1000) {
    processError.value = '처리 의견은 1,000자까지 입력할 수 있습니다.'
    return
  }

  const confirmMessage =
    status === 'RESOLVED'
      ? '신고를 수용하고 대상 게시글을 숨김 처리하시겠습니까?'
      : '신고를 기각하시겠습니까? 게시글의 현재 공개·숨김 상태는 유지됩니다.'

  if (!window.confirm(confirmMessage)) return

  processing.value = true

  try {
    // 처리자와 처리 시각은 서버에서 설정한다.
    // 공통 request()가 변경 요청에 필요한 CSRF 토큰을 추가한다.
    await request<void>(`${baseUrl}/${detail.id}`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        status,
        processNote: note || null,
      }),
    })
  } catch (error: unknown) {
    // 실패 시 입력한 처리 의견과 상세 내용을 유지한다.
    showProcessError(error)
    return
  } finally {
    processing.value = false
  }

  notice.value =
    status === 'RESOLVED' ? '신고를 수용하고 게시글을 숨김 처리했습니다.' : '신고를 기각했습니다.'

  // 처리 후 목록 건수가 줄어들 수 있으므로 첫 페이지로 이동한다.
  resetPage()
  await loadData()

  // 신고 처리 성공과 목록 재조회 실패를 구분해 안내한다.
  if (errorMessage.value) {
    errorMessage.value = `신고 처리는 완료됐지만 목록을 다시 불러오지 못했습니다. ${errorMessage.value}`
  }
}

// 처리 상태 코드를 한글 명칭으로 변환한다.
function statusLabel(status: ReportStatus): string {
  switch (status) {
    case 'PENDING':
      return '처리 대기'
    case 'RESOLVED':
      return '처리 완료'
    case 'REJECTED':
      return '기각'
    default:
      return status
  }
}

// 신고 사유 코드를 한글 명칭으로 변환한다.
function reasonLabel(reason: string): string {
  switch (reason) {
    case 'SPAM':
      return '스팸·홍보'
    case 'ABUSE':
      return '욕설·비방'
    case 'INAPPROPRIATE':
      return '부적절한 내용'
    case 'OTHER':
      return '기타'
    default:
      return reason
  }
}

/**
 * UTC로 저장된 시각을 한국 시간으로 표시한다.
 * 시간대가 없는 LocalDateTime 응답에는 UTC를 나타내는 Z를 붙인다.
 */
function formatCreatedDate(value: string): string {
  const utcValue = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`

  const date = new Date(utcValue)
  if (Number.isNaN(date.getTime())) return '—'

  return date.toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

// 최초 진입 시 처리 대기 신고를 조회한다.
onMounted(loadData)
</script>

<template>
  <section class="crud-page admin-report-page" :aria-busy="loading || processing">
    <header class="page-heading">
      <div class="page-icon">
        <Flag :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>신고 관리</h1>
        <p>커뮤니티 신고 내용을 확인하고 처리합니다.</p>
      </div>
    </header>

    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <!-- 신고 목록과 상태 필터 -->
    <section class="card">
      <div class="card-heading">
        <div class="heading-title">
          <Flag :size="20" aria-hidden="true" />
          <h2>신고 목록</h2>
          <span v-if="!loading && !errorMessage" class="count-badge">
            {{ totalCount.toLocaleString() }}건
          </span>
        </div>

        <form class="report-search" @submit.prevent="searchReports">
          <label for="report-search-status">처리 상태</label>

          <select
            id="report-search-status"
            v-model="searchStatus"
            :disabled="loading || processing"
          >
            <option value="ALL">전체</option>
            <option value="PENDING">처리 대기</option>
            <option value="RESOLVED">처리 완료</option>
            <option value="REJECTED">기각</option>
          </select>

          <button type="submit" class="btn btn-primary" :disabled="loading || processing">
            <Search :size="16" aria-hidden="true" />
            조회
          </button>
        </form>
      </div>

      <p v-if="loading" class="empty-state" role="status">신고 목록을 불러오는 중입니다.</p>

      <template v-else-if="!errorMessage">
        <p v-if="reports.length === 0" class="empty-state">조회된 신고가 없습니다.</p>

        <div v-else class="table-wrap">
          <table class="data-table report-table">
            <thead>
              <tr>
                <th scope="col">게시글 제목</th>
                <th scope="col">신고자</th>
                <th scope="col">신고 사유</th>
                <th scope="col">신고일</th>
                <th scope="col">처리 상태</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="report in reports" :key="report.id">
                <td class="report-title">
                  <button
                    type="button"
                    class="report-title-button"
                    :disabled="processing"
                    :aria-expanded="selectedReportId === report.id"
                    :aria-controls="selectedReportId === report.id ? 'report-detail' : undefined"
                    @click="openReportDetail(report.id)"
                  >
                    {{ report.title }}
                  </button>
                </td>
                <td>{{ report.reporterNickname }}</td>
                <td>{{ reasonLabel(report.reason) }}</td>
                <td class="date-cell">
                  {{ formatCreatedDate(report.createdDt) }}
                </td>
                <td>
                  <span
                    class="badge report-status"
                    :class="{
                      'status-pending': report.status === 'PENDING',
                      'status-resolved': report.status === 'RESOLVED',
                      'status-rejected': report.status === 'REJECTED',
                    }"
                  >
                    {{ statusLabel(report.status) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <Pagination
          :page="page"
          :total-pages="totalPages"
          :disabled="loading || processing"
          @change="changePage"
        />
      </template>
    </section>

    <!-- 선택한 신고의 상세 정보 -->
    <section
      v-if="selectedReportId !== null"
      id="report-detail"
      class="card report-detail-card"
      aria-labelledby="report-detail-title"
      :aria-busy="detailLoading || processing"
    >
      <div class="card-heading">
        <div class="heading-title">
          <Flag :size="20" aria-hidden="true" />
          <h2 id="report-detail-title">신고 상세</h2>
          <span class="heading-note"> 신고 번호 {{ selectedReportId }} </span>
        </div>

        <button
          type="button"
          class="btn btn-secondary"
          :disabled="processing"
          @click="closeReportDetail"
        >
          닫기
        </button>
      </div>

      <p v-if="detailLoading" class="empty-state" role="status">
        신고 상세 정보를 불러오는 중입니다.
      </p>

      <p v-else-if="detailError" class="message error-message" role="alert">
        {{ detailError }}
      </p>

      <template v-else-if="reportDetail">
        <dl class="report-detail-grid">
          <div>
            <dt>신고자</dt>
            <dd>{{ reportDetail.reporterNickname }}</dd>
          </div>

          <div>
            <dt>신고일</dt>
            <dd>{{ formatCreatedDate(reportDetail.createdDt) }}</dd>
          </div>

          <div>
            <dt>신고 사유</dt>
            <dd>{{ reasonLabel(reportDetail.reason) }}</dd>
          </div>

          <div>
            <dt>처리 상태</dt>
            <dd>
              <span
                class="badge report-status"
                :class="{
                  'status-pending': reportDetail.status === 'PENDING',
                  'status-resolved': reportDetail.status === 'RESOLVED',
                  'status-rejected': reportDetail.status === 'REJECTED',
                }"
              >
                {{ statusLabel(reportDetail.status) }}
              </span>
            </dd>
          </div>
        </dl>

        <section class="report-detail-section">
          <h3>신고 상세 설명</h3>
          <p class="report-text">
            {{ reportDetail.description?.trim() || '등록된 상세 설명이 없습니다.' }}
          </p>
        </section>

        <section class="report-detail-section">
          <h3>신고 대상 게시글</h3>

          <dl class="report-detail-grid">
            <div>
              <dt>작성자</dt>
              <dd>{{ reportDetail.authorNickname }}</dd>
            </div>
            <div>
              <dt>현재 공개 상태</dt>
              <dd>{{ reportDetail.isHidden ? '숨김' : '공개' }}</dd>
            </div>
          </dl>

          <h4 class="reported-post-title">{{ reportDetail.title }}</h4>

          <!-- 사용자 입력은 HTML로 해석하지 않고 텍스트로 표시한다. -->
          <p class="report-text">{{ reportDetail.content }}</p>
        </section>

        <!-- 처리 대기 신고에만 처리 입력과 버튼을 표시한다. -->
        <section v-if="reportDetail.status === 'PENDING'" class="report-detail-section">
          <h3>신고 처리</h3>

          <fieldset :disabled="processing">
            <legend class="sr-only">신고 처리 의견 및 처리 방법</legend>

            <div class="form-grid">
              <label class="memo-field" for="report-process-note">
                처리 의견 (선택)
                <textarea
                  id="report-process-note"
                  v-model="processNote"
                  maxlength="1000"
                  rows="4"
                  placeholder="신고를 수용하거나 기각하는 이유를 입력해 주세요."
                ></textarea>
              </label>
            </div>

            <p class="process-note-count">{{ processNote.length.toLocaleString() }} / 1,000자</p>

            <p v-if="processError" class="message error-message" role="alert">
              {{ processError }}
            </p>

            <div class="form-actions">
              <button type="button" class="btn btn-secondary" @click="processReport('REJECTED')">
                기각
              </button>

              <button
                type="button"
                class="btn report-resolve-button"
                @click="processReport('RESOLVED')"
              >
                신고 수용·게시글 숨김
              </button>
            </div>
          </fieldset>

          <p v-if="processing" class="process-progress" role="status">신고를 처리하고 있습니다.</p>
        </section>

        <!-- 처리 완료 또는 기각된 신고에는 기존 처리 이력을 표시한다. -->
        <section v-else class="report-detail-section">
          <h3>처리 이력</h3>

          <dl class="report-detail-grid">
            <div>
              <dt>처리자 ID</dt>
              <dd>{{ reportDetail.processedBy ?? '—' }}</dd>
            </div>
            <div>
              <dt>처리 일시</dt>
              <dd>
                {{ reportDetail.processedDt ? formatCreatedDate(reportDetail.processedDt) : '—' }}
              </dd>
            </div>
          </dl>

          <h4 class="process-note-title">처리 의견</h4>
          <p class="report-text">
            {{ reportDetail.processNote?.trim() || '등록된 처리 의견이 없습니다.' }}
          </p>
        </section>
      </template>
    </section>
  </section>
</template>

<style scoped>
/* 상태 필터와 조회 버튼을 목록 제목의 오른쪽에 배치한다. */
.report-search {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-left: auto;
}

.report-search label {
  color: #475569;
  font-size: 13px;
  white-space: nowrap;
}

.report-search select {
  min-width: 130px;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background-color: #fff;
  color: #1e293b;
  font: inherit;
  font-size: 14px;
}

.report-search select:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.report-search select:disabled {
  background-color: #f8fafc;
  cursor: not-allowed;
}

/* 좁은 화면에서는 공통 table-wrap 안에서 가로 스크롤한다. */
.report-table {
  min-width: 760px;
}

.report-title {
  min-width: 220px;
  max-width: 420px;
  overflow-wrap: anywhere;
}

.report-title-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #1e293b;
  font: inherit;
  font-weight: 600;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.report-title-button:hover:enabled {
  color: #2563eb;
  text-decoration: underline;
}

.report-title-button:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 4px;
  border-radius: 3px;
}

.report-status {
  white-space: nowrap;
}

.status-pending {
  background-color: #fff7ed;
  color: #9a3412;
}

.status-resolved {
  background-color: #ecfdf5;
  color: #047857;
}

.status-rejected {
  background-color: #f1f5f9;
  color: #475569;
}

/* 신고 상세의 항목명과 값을 두 열로 표시한다. */
.report-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin: 0;
}

.report-detail-grid > div {
  min-width: 0;
}

.report-detail-grid dt {
  margin-bottom: 6px;
  color: #64748b;
  font-size: 13px;
}

.report-detail-grid dd {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.report-detail-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e2e8f0;
}

.report-detail-section h3 {
  margin: 0 0 16px;
  font-size: 16px;
}

.reported-post-title,
.process-note-title {
  margin: 20px 0 12px;
  font-size: 14px;
  overflow-wrap: anywhere;
}

/* 신고 설명과 게시글 본문의 줄바꿈을 유지한다. */
.report-text {
  margin: 0;
  padding: 16px;
  border-radius: 8px;
  background-color: #f8fafc;
  color: #334155;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.process-note-count {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  text-align: right;
}

.admin-report-page .report-resolve-button {
  border: 1px solid #dc2626;
  background-color: #dc2626;
  color: #fff;
}

.admin-report-page .report-resolve-button:hover:enabled {
  border-color: #b91c1c;
  background-color: #b91c1c;
}

.report-resolve-button:focus-visible {
  outline: 3px solid #fecaca;
  outline-offset: 2px;
}

.report-resolve-button:disabled,
.report-title-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.process-progress {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 560px) {
  .admin-report-page .page-heading h1 {
    font-size: 24px;
  }

  .report-search {
    width: 100%;
    margin-left: 0;
  }

  .report-search select {
    flex: 1;
    min-width: 0;
  }

  .report-detail-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
}
</style>
