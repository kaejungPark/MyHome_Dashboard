<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request, ApiError } from '../api/client'
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { MessageSquare } from '@lucide/vue'

interface CommunityDetailResponse {
  id: number
  userId: number
  nickname: string
  title: string
  content: string
  viewCount: number
  createdDt: string
  updatedDt: string
}

const route = useRoute()
const router = useRouter()

// /community/1의 "1"을 숫자로 변환한다.
const communityId = computed(() => Number(route.params.id))

// 조회한 게시글 상세 정보
const details = ref<CommunityDetailResponse | null>(null)

const loginUserId = ref<number | null>(null) // 버튼 표시 판단에 필요한 로그인 사용자 ID.
const authError = ref('')

const loading = ref(false)
const errorMessage = ref('') // 오류 메시지
const baseUrl = '/api/community'

const deleting = ref(false) // 삭제 중 중복 요청 방지
const deleteError = ref('') // 삭제 실패 안내

type ReportReason = 'SPAM' | 'ABUSE' | 'INAPPROPRIATE' | 'OTHER'
const reportDialog = ref<HTMLDialogElement | null>(null)
const reportReason = ref<ReportReason>('SPAM')
const reportDescription = ref('')
const reporting = ref(false)
const reportError = ref('')
const reportMessage = ref('')

// 로그인 사용자와 게시글 작성자가 같으면 본인 글이다.
const isOwner = computed(
  () => loginUserId.value !== null && details.value?.userId === loginUserId.value,
)

// 로그인한 사용자는 다른 사람의 게시글만 신고할 수 있다.
const canReport = computed(
  () => loginUserId.value !== null && details.value !== null && !isOwner.value && !authError.value,
)

// 발생한 오류를 화면에 표시할 메시지로 변환한다.
function showError(error: unknown) {
  errorMessage.value =
    error instanceof Error ? error.message : '게시글 조회 중 오류가 발생했습니다.'
}

// 새 신고를 시작할 때 이전 입력과 안내를 초기화한다.
function openReport() {
  if (!canReport.value || reporting.value) return
  reportReason.value = 'SPAM'
  reportDescription.value = ''
  reportError.value = ''
  reportMessage.value = ''
  reportDialog.value?.showModal()
}

// 전송 중에는 팝업을 유지해 처리 결과를 확인할 수 있게 한다.
function closeReport() {
  if (!reporting.value) reportDialog.value?.close()
}

async function submitReport() {
  if (reporting.value || !canReport.value || !details.value) return
  reportError.value = ''
  const description = reportDescription.value.trim()
  if (description.length > 1000) {
    reportError.value = '상세 설명은 1,000자 이내로 입력해 주세요.'
    return
  }

  reporting.value = true
  try {
    // 공통 요청 함수가 CSRF 토큰을 첨부하며, 신고자 ID는 서버에서 판단한다.
    await request<null>(`${baseUrl}/${details.value.id}/reports`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: reportReason.value, description: description || null }),
    })
    reportDialog.value?.close()
    reportMessage.value = '신고가 접수되었습니다.'
  } catch (error: unknown) {
    // 중복 신고를 포함한 서버 오류 메시지를 팝업 안에 표시한다.
    reportError.value = error instanceof Error ? error.message : '신고 접수에 실패했습니다.'
    if (error instanceof ApiError && error.status === 401) loginUserId.value = null
  } finally {
    reporting.value = false
  }
}

// 게시글 ID를 검증하고 상세 정보를 조회
async function findDetails() {
  if (!Number.isSafeInteger(communityId.value) || communityId.value <= 0) {
    throw new Error('올바르지 않은 게시글 번호입니다.')
  }

  const result = await request<CommunityDetailResponse>(`${baseUrl}/${communityId.value}`)

  if (result === null) {
    throw new Error('게시글 상세 응답이 없습니다.')
  }

  details.value = result
}

// 본인 글 삭제를 확인받고 서버에 요청한다.
async function deleteCommunity() {
  if (deleting.value || !isOwner.value || !details.value) return

  if (!window.confirm('게시글을 삭제할까요? 삭제한 글은 복구할 수 없습니다.')) {
    return
  }

  deleting.value = true
  deleteError.value = ''

  try {
    // 공통 request가 CSRF 토큰을 처리한다.
    await request<null>(`${baseUrl}/${details.value.id}`, {
      method: 'DELETE',
    })

    // 삭제한 상세 화면 대신 목록으로 이동한다.
    await router.replace('/community')
  } catch (error: unknown) {
    deleteError.value = error instanceof Error ? error.message : '게시글 삭제에 실패했습니다.'
  } finally {
    deleting.value = false
  }
}

// 비로그인은 정상 상태로 처리하며, 다른 오류는 따로 안내한다.
async function findLoginUser() {
  loginUserId.value = null
  authError.value = ''

  try {
    const user = await request<{ id: number }>('/api/auth/me')

    if (user === null) {
      throw new Error('로그인 사용자 정보가 없습니다.')
    }

    loginUserId.value = user.id
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) return

    authError.value = '로그인 상태를 확인하지 못했습니다. 새로고침해 주세요.'
  }
}

// DB의 UTC 일시를 한국 시간으로 표시한다.
function formatDateTime(value: string): string {
  const utcValue = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`

  const date = new Date(utcValue)
  if (Number.isNaN(date.getTime())) return '—'

  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

// 조회 상태 초기화 및 로딩·오류 처리
async function loadData() {
  if (loading.value) return

  errorMessage.value = ''
  details.value = null

  loading.value = true

  try {
    await findDetails()
    await findLoginUser()
  } catch (error: unknown) {
    showError(error)
  } finally {
    // 성공·실패 여부와 관계없이 조회 상태를 해제한다.
    loading.value = false
  }
}

// 화면 최초 진입 시 상세 조회
onMounted(loadData)
</script>
<template>
  <section class="crud-page community-detail-page">
    <!-- 화면 제목 -->
    <header class="page-heading">
      <div class="page-icon">
        <MessageSquare :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>커뮤니티</h1>
        <p>자취 생활의 이야기와 유용한 정보를 나눠보세요.</p>
      </div>
    </header>
    <!-- 조회 상태에 따라 안내 또는 게시글을 표시한다. -->
    <p v-if="loading" class="empty-state" role="status">게시글을 불러오는 중입니다.</p>

    <p v-else-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <article v-else-if="details" class="card post-detail">
      <header class="post-header">
        <h2 class="post-title">{{ details.title }}</h2>
        <div class="post-meta">
          <span>작성자 {{ details.nickname }}</span>
          <span>조회수 {{ details.viewCount.toLocaleString('ko-KR') }}</span>
          <span>작성 {{ formatDateTime(details.createdDt) }}</span>
          <span v-if="details.updatedDt !== details.createdDt">
            수정 {{ formatDateTime(details.updatedDt) }}
          </span>
        </div>
      </header>

      <!-- 본문을 일반 텍스트로 표시한다. -->
      <div class="post-content">{{ details.content }}</div>

      <p v-if="authError" class="message error-message" role="alert">
        {{ authError }}
      </p>

      <p v-if="deleteError" class="message error-message" role="alert">
        {{ deleteError }}
      </p>

      <p v-if="reportMessage" class="report-success" role="status">{{ reportMessage }}</p>

      <footer class="post-actions">
        <RouterLink to="/community" class="btn btn-secondary"> 목록으로 </RouterLink>

        <button v-if="canReport" type="button" class="btn btn-secondary" @click="openReport">
          신고
        </button>

        <!-- 실제 권한 검사는 수정·삭제 API에서도 수행한다. -->
        <div v-if="isOwner" class="owner-actions">
          <button
            type="button"
            class="btn btn-edit"
            :disabled="deleting"
            @click="
              router.push({
                name: 'community-edit',
                params: { id: details.id },
              })
            "
          >
            수정
          </button>
          <button
            type="button"
            class="btn btn-delete"
            :disabled="deleting"
            @click="deleteCommunity"
          >
            {{ deleting ? '삭제 중…' : '삭제' }}
          </button>
        </div>
      </footer>
    </article>
  </section>

  <!-- 네이티브 dialog로 팝업 밖의 조작을 막고 키보드 초점을 유지한다. -->
  <Teleport to="body">
    <dialog
      ref="reportDialog"
      class="report-dialog"
      aria-labelledby="report-title"
      @cancel="reporting && $event.preventDefault()"
    >
      <form @submit.prevent="submitReport" :aria-busy="reporting">
        <h2 id="report-title">게시글 신고</h2>
        <p class="report-help">신고 사유를 선택해 주세요. 접수된 내용은 관리자가 확인합니다.</p>
        <fieldset :disabled="reporting">
          <label for="report-reason">신고 사유</label>
          <select id="report-reason" v-model="reportReason" required>
            <option value="SPAM">스팸·홍보</option>
            <option value="ABUSE">욕설·비방</option>
            <option value="INAPPROPRIATE">부적절한 내용</option>
            <option value="OTHER">기타</option>
          </select>
          <label for="report-description">상세 설명 (선택)</label>
          <textarea
            id="report-description"
            v-model="reportDescription"
            rows="5"
            maxlength="1000"
            placeholder="신고 내용을 설명해 주세요."
            aria-describedby="report-count"
          ></textarea>
          <p id="report-count" class="report-count">{{ reportDescription.length }} / 1,000자</p>
        </fieldset>
        <p v-if="reportError" class="message error-message" role="alert">{{ reportError }}</p>
        <div class="report-actions">
          <button
            type="button"
            class="btn btn-secondary"
            :disabled="reporting"
            @click="closeReport"
          >
            취소
          </button>
          <button type="submit" class="btn btn-primary" :disabled="reporting || !canReport">
            {{ reporting ? '접수 중…' : '신고하기' }}
          </button>
        </div>
      </form>
    </dialog>
  </Teleport>
</template>
<style scoped>
.post-header {
  padding-bottom: 20px;
  border-bottom: 1px solid #e2e8f0;
}

.post-title {
  margin: 0 0 16px;
  color: #1e293b;
  font-size: 24px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  color: #64748b;
  font-size: 13px;
}

/* 입력한 줄바꿈은 유지하고 긴 문자열은 화면 안에서 줄바꿈한다. */
.post-content {
  min-height: 240px;
  padding: 24px 0;
  color: #334155;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.post-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.post-actions a {
  text-decoration: none;
}

.owner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 560px) {
  .post-title {
    font-size: 20px;
  }
}
/* 작은 화면에서도 팝업 내용과 버튼에 접근할 수 있도록 크기를 제한한다. */
.report-dialog {
  width: min(480px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  box-sizing: border-box;
  overflow-y: auto;
  padding: 24px;
  border: 1px solid #dbe3ef;
  border-radius: 16px;
  color: #1e293b;
  background: #fff;
}
.report-dialog::backdrop {
  background: rgb(15 23 42 / 45%);
}
.report-dialog h2 {
  margin: 0 0 12px;
  font-size: 22px;
}
.report-help {
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}
.report-dialog fieldset {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 0;
  margin: 20px 0;
  border: 0;
}
.report-dialog label {
  font-size: 14px;
  font-weight: 600;
}
.report-dialog select,
.report-dialog textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: inherit;
  background: #fff;
  font: inherit;
}
.report-dialog textarea {
  resize: vertical;
}
.report-count {
  margin: 0;
  text-align: right;
  color: #64748b;
  font-size: 12px;
}
.report-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.report-success {
  padding: 12px 16px;
  color: #166534;
  background: #f0fdf4;
  border-radius: 8px;
}
</style>
