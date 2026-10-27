<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { EyeOff } from '@lucide/vue'
import { request } from '../api/client'
import type { PageResponse } from '../types/page'
import Pagination from '../components/Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { useErrorMessage } from '../composables/useErrorMessage'

// 숨김 게시글 목록에 표시할 한 항목이다.
interface HiddenCommunity {
  id: number // 게시글 ID
  title: string // 게시글 제목
  authorNickname: string | null // 작성자 닉네임. 없으면 null
  createdDt: string // 게시글 작성 시각(UTC)
  hiddenBy: number | null // 숨김 처리한 관리자 ID
  hiddenDt: string | null // 숨김 처리 시각(UTC)
}

// 목록 정보에 본문과 현재 숨김 상태를 추가한 상세 정보다.
interface HiddenCommunityDetail extends HiddenCommunity {
  content: string // 게시글 본문
  isHidden: boolean // true이면 숨김, false이면 공개 상태
}

const baseUrl = '/api/admin/community/hidden'

// 목록 조회 상태
const communities = ref<HiddenCommunity[]>([])
const loading = ref(false)
const { errorMessage, showError } = useErrorMessage('숨김 게시글 목록을 불러오지 못했습니다.')

const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(10)

// 상세 조회 상태
const selectedCommunityId = ref<number | null>(null)
const communityDetail = ref<HiddenCommunityDetail | null>(null)
const detailLoading = ref(false)
const { errorMessage: detailError, showError: showDetailError } =
  useErrorMessage('게시글 상세 정보를 불러오지 못했습니다.')

// 숨김 해제 상태
const restoring = ref(false)
const notice = ref('')
const { errorMessage: restoreError, showError: showRestoreError } =
  useErrorMessage('게시글 숨김 해제에 실패했습니다.')

// 이전 상세 조회 응답이 나중에 도착해 화면을 덮어쓰는 것을 방지한다.
let detailRequestSequence = 0

/**
 * 상세 카드를 닫고 이전 조회 응답을 무효화한다.
 * 숨김 해제 중에는 상세를 닫지 않는다.
 */
function closeDetail() {
  if (restoring.value) return

  detailRequestSequence++
  selectedCommunityId.value = null
  communityDetail.value = null
  detailLoading.value = false
  detailError.value = ''
  restoreError.value = ''
}

/**
 * 숨김 게시글 목록을 페이지 조회한다.
 * 목록이 바뀌면 이전 게시글 상세는 닫는다.
 */
async function loadData() {
  if (loading.value || restoring.value) return

  closeDetail()

  loading.value = true
  errorMessage.value = ''
  communities.value = []
  resetTotals()

  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: String(size.value),
    })

    const result = await request<PageResponse<HiddenCommunity>>(`${baseUrl}?${params.toString()}`)

    if (result === null) {
      throw new Error('숨김 게시글 목록 정보를 받지 못했습니다.')
    }

    // 조회한 게시글 목록과 페이지 정보를 각각 화면 상태에 반영한다.
    communities.value = result.items
    applyPage(result)
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 목록 조회 실패 시 현재 페이지를 다시 조회한다.
async function retryLoad() {
  if (loading.value || restoring.value) return

  await loadData()
}

// 이동 가능한 페이지인지 확인한 뒤 목록을 조회한다.
async function changePage(targetPage: number) {
  if (loading.value || restoring.value || !canMoveTo(targetPage)) {
    return
  }

  notice.value = ''
  page.value = targetPage
  await loadData()
}

/**
 * 게시글 ID로 관리자용 상세 정보를 조회한다.
 * 가장 최근에 선택한 게시글의 응답만 화면에 반영한다.
 */
async function openDetail(communityId: number) {
  if (loading.value || restoring.value) return

  const requestSequence = ++detailRequestSequence

  selectedCommunityId.value = communityId
  communityDetail.value = null
  detailLoading.value = true
  detailError.value = ''
  restoreError.value = ''
  notice.value = ''

  try {
    const result = await request<HiddenCommunityDetail>(`${baseUrl}/${communityId}`)

    if (requestSequence !== detailRequestSequence) return

    if (result === null) {
      throw new Error('게시글 상세 정보를 받지 못했습니다.')
    }

    communityDetail.value = result
  } catch (error: unknown) {
    if (requestSequence !== detailRequestSequence) return

    showDetailError(error)
  } finally {
    if (requestSequence === detailRequestSequence) {
      detailLoading.value = false
    }
  }
}

/**
 * 선택한 게시글의 숨김을 해제한다.
 * 기존 신고 처리 결과와 의견은 변경하지 않는다.
 */
async function restoreCommunity() {
  if (restoring.value || loading.value || detailLoading.value) return

  const detail = communityDetail.value
  if (!detail) return

  restoreError.value = ''
  notice.value = ''

  if (!detail.isHidden) {
    restoreError.value = '이미 공개 상태인 게시글입니다.'
    return
  }

  const confirmed = window.confirm(
    '게시글의 숨김을 해제하시겠습니까? 일반 커뮤니티에 다시 공개됩니다.',
  )

  if (!confirmed) return

  restoring.value = true

  try {
    // 요청 본문은 필요 없으며 CSRF 토큰은 공통 request()에서 추가한다.
    await request<void>(`${baseUrl}/${detail.id}/restore`, {
      method: 'PATCH',
    })
  } catch (error: unknown) {
    // 실패하면 상세 내용을 유지하고 처리 오류만 표시한다.
    showRestoreError(error)
    return
  } finally {
    restoring.value = false
  }

  notice.value = '게시글의 숨김을 해제했습니다.'

  // 숨김 목록에서 항목이 빠지므로 첫 페이지부터 다시 조회한다.
  resetPage()
  await loadData()

  // 숨김 해제 성공과 목록 재조회 실패를 구분한다.
  if (errorMessage.value) {
    errorMessage.value = `숨김 해제는 완료됐지만 목록을 다시 불러오지 못했습니다. ${errorMessage.value}`
  }
}

/**
 * UTC로 저장된 시각을 한국 시간으로 표시한다.
 * 시간대가 없는 LocalDateTime 응답에는 UTC를 나타내는 Z를 붙인다.
 */
function formatDateTime(value: string | null): string {
  if (!value) return '—'

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

// 화면에 진입하면 숨김 게시글 목록을 조회한다.
onMounted(loadData)
</script>

<template>
  <section class="crud-page hidden-community-page" :aria-busy="loading || restoring">
    <header class="page-heading">
      <div class="page-icon">
        <EyeOff :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>숨김 게시글</h1>
        <p>숨김 처리된 게시글을 확인하고 공개 여부를 관리합니다.</p>
      </div>
    </header>

    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <div v-if="errorMessage" class="list-error">
      <p class="message error-message" role="alert">
        {{ errorMessage }}
      </p>
      <button
        type="button"
        class="btn btn-secondary"
        :disabled="loading || restoring"
        @click="retryLoad"
      >
        다시 조회
      </button>
    </div>

    <!-- 숨김 게시글 목록 -->
    <section class="card">
      <div class="card-heading">
        <div class="heading-title">
          <EyeOff :size="20" aria-hidden="true" />
          <h2>숨김 게시글 목록</h2>

          <span v-if="!loading && !errorMessage" class="count-badge">
            {{ totalCount.toLocaleString() }}건
          </span>
        </div>

        <span class="heading-note"> 제목을 누르면 게시글 내용을 확인할 수 있습니다. </span>
      </div>

      <p v-if="loading" class="empty-state" role="status">숨김 게시글 목록을 불러오는 중입니다.</p>

      <template v-else-if="!errorMessage">
        <p v-if="communities.length === 0" class="empty-state">조회된 숨김 게시글이 없습니다.</p>

        <div v-else class="table-wrap">
          <table class="data-table hidden-community-table">
            <thead>
              <tr>
                <th scope="col">게시글 제목</th>
                <th scope="col">작성자</th>
                <th scope="col">작성일</th>
                <th scope="col">숨김 처리일</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="community in communities" :key="community.id">
                <td class="community-title">
                  <button
                    type="button"
                    class="community-title-button"
                    :disabled="restoring"
                    :aria-expanded="selectedCommunityId === community.id"
                    :aria-controls="
                      selectedCommunityId === community.id ? 'hidden-community-detail' : undefined
                    "
                    @click="openDetail(community.id)"
                  >
                    {{ community.title }}
                  </button>
                </td>
                <td>{{ community.authorNickname ?? '—' }}</td>
                <td class="date-cell">
                  {{ formatDateTime(community.createdDt) }}
                </td>
                <td class="date-cell">
                  {{ formatDateTime(community.hiddenDt) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <Pagination
          :page="page"
          :total-pages="totalPages"
          :disabled="loading || restoring"
          @change="changePage"
        />
      </template>
    </section>

    <!-- 선택한 숨김 게시글의 상세 정보 -->
    <section
      v-if="selectedCommunityId !== null"
      id="hidden-community-detail"
      class="card"
      aria-labelledby="hidden-community-detail-title"
      :aria-busy="detailLoading || restoring"
    >
      <div class="card-heading">
        <div class="heading-title">
          <EyeOff :size="20" aria-hidden="true" />
          <h2 id="hidden-community-detail-title">게시글 상세</h2>
          <span class="heading-note"> 게시글 번호 {{ selectedCommunityId }} </span>
        </div>

        <button type="button" class="btn btn-secondary" :disabled="restoring" @click="closeDetail">
          닫기
        </button>
      </div>

      <p v-if="detailLoading" class="empty-state" role="status">
        게시글 상세 정보를 불러오는 중입니다.
      </p>

      <p v-else-if="detailError" class="message error-message" role="alert">
        {{ detailError }}
      </p>

      <template v-else-if="communityDetail">
        <dl class="community-detail-grid">
          <div>
            <dt>작성자</dt>
            <dd>{{ communityDetail.authorNickname ?? '—' }}</dd>
          </div>
          <div>
            <dt>작성일</dt>
            <dd>{{ formatDateTime(communityDetail.createdDt) }}</dd>
          </div>
          <div>
            <dt>숨김 처리자 ID</dt>
            <dd>{{ communityDetail.hiddenBy ?? '—' }}</dd>
          </div>
          <div>
            <dt>숨김 처리일</dt>
            <dd>{{ formatDateTime(communityDetail.hiddenDt) }}</dd>
          </div>
          <div>
            <dt>현재 공개 상태</dt>
            <dd>
              <span
                class="badge"
                :class="communityDetail.isHidden ? 'status-hidden' : 'status-public'"
              >
                {{ communityDetail.isHidden ? '숨김' : '공개' }}
              </span>
            </dd>
          </div>
        </dl>

        <section class="community-detail-section">
          <h3 class="detail-post-title">{{ communityDetail.title }}</h3>

          <!-- 본문을 HTML로 해석하지 않고 텍스트로 표시한다. -->
          <p class="community-content">{{ communityDetail.content }}</p>
        </section>

        <section v-if="communityDetail.isHidden" class="community-detail-section">
          <h3>숨김 해제</h3>
          <p class="restore-description">
            숨김을 해제하면 일반 커뮤니티 목록과 상세 화면에 다시 표시됩니다. 기존 신고 처리 결과와
            의견은 유지됩니다.
          </p>

          <p v-if="restoreError" class="message error-message" role="alert">
            {{ restoreError }}
          </p>

          <div class="form-actions">
            <button
              type="button"
              class="btn btn-primary"
              :disabled="restoring"
              @click="restoreCommunity"
            >
              {{ restoring ? '처리 중...' : '숨김 해제' }}
            </button>
          </div>

          <p v-if="restoring" class="restore-progress" role="status">
            게시글의 숨김을 해제하고 있습니다.
          </p>
        </section>
      </template>
    </section>
  </section>
</template>

<style scoped>
.list-error {
  margin-bottom: 24px;
}

/* 좁은 화면에서는 공통 table-wrap 안에서 가로 스크롤한다. */
.hidden-community-table {
  min-width: 720px;
}

.community-title {
  min-width: 220px;
  max-width: 420px;
  overflow-wrap: anywhere;
}

.community-title-button {
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

.community-title-button:hover:enabled {
  color: #2563eb;
  text-decoration: underline;
}

.community-title-button:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 4px;
  border-radius: 3px;
}

.community-title-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.community-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin: 0;
}

.community-detail-grid > div {
  min-width: 0;
}

.community-detail-grid dt {
  margin-bottom: 6px;
  color: #64748b;
  font-size: 13px;
}

.community-detail-grid dd {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.status-hidden {
  background-color: #fef2f2;
  color: #b91c1c;
}

.status-public {
  background-color: #ecfdf5;
  color: #047857;
}

.community-detail-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e2e8f0;
}

.community-detail-section h3 {
  margin: 0 0 16px;
  font-size: 16px;
}

.detail-post-title {
  overflow-wrap: anywhere;
}

/* 본문의 줄바꿈을 유지하고 긴 문자열은 영역 안에서 줄바꿈한다. */
.community-content {
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

.restore-description {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.restore-progress {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 560px) {
  .hidden-community-page .page-heading h1 {
    font-size: 24px;
  }

  .community-detail-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
}
</style>
