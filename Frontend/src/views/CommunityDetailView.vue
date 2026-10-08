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

// 로그인 사용자와 게시글 작성자가 같으면 본인 글이다.
const isOwner = computed(
  () => loginUserId.value !== null && details.value?.userId === loginUserId.value,
)

// 발생한 오류를 화면에 표시할 메시지로 변환한다.
function showError(error: unknown) {
  errorMessage.value =
    error instanceof Error ? error.message : '게시글 조회 중 오류가 발생했습니다.'
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

      <footer class="post-actions">
        <RouterLink to="/community" class="btn btn-secondary"> 목록으로 </RouterLink>

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
</style>
