<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Pagination from './Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { ApiError } from '../api/client'
import {
  fetchComments,
  fetchReplies,
  createComment,
  updateComment,
  deleteComment,
} from '../api/comment'
import type { CommentResponse } from '../types/comment'

/**
 * 게시글 상세 화면에서 전달받는 값이다.
 * 로그인 정보 조회에 실패한 경우에는 쓰기 기능을 허용하지 않는다.
 */
const props = defineProps<{
  communityId: number
  loginUserId: number | null
  authError: string
}>()

/** 세션 만료 시 부모 화면도 로그인 사용자 정보를 초기화하도록 알린다. */
const emit = defineEmits<{
  (event: 'session-expired'): void
}>()

/**
 * 부모 댓글 하나에 속한 대댓글의 화면 상태다.
 * 부모마다 페이지·목록·입력값을 따로 관리한다.
 */
interface ReplyState {
  open: boolean
  loading: boolean
  loaded: boolean
  page: number
  size: number
  totalCount: number
  totalPages: number
  items: CommentResponse[]
  content: string
  error: string
}

/** 일반 댓글과 해당 댓글의 대댓글 상태를 함께 관리한다. */
interface CommentRow {
  comment: CommentResponse
  replies: ReplyState
}

const rows = ref<CommentRow[]>([])
const loading = ref(false)
const saving = ref(false)
const sessionExpired = ref(false)

/** 목록 조회 오류와 변경 요청 오류를 분리해 표시한다. */
const listError = ref('')
const actionError = ref('')
const successMessage = ref('')

/** 새 일반 댓글 입력값 */
const content = ref('')

/**
 * 한 번에 하나의 댓글만 수정한다.
 * 일반 댓글과 대댓글 모두 같은 수정 입력 상태를 사용한다.
 */
const editingId = ref<number | null>(null)
const editingContent = ref('')

const { page, size, totalCount, totalPages, applyPage, canMoveTo } = usePagination(5)

/** 버튼 표시용 판단이며 최종 권한은 서버에서 검사한다. */
const canWrite = computed(
  () => props.loginUserId !== null && !props.authError && !sessionExpired.value,
)

/** 목록 조회 또는 변경 요청 중 중복 조작을 막는다. */
const busy = computed(() => loading.value || saving.value)

/** 본인이 작성한 삭제되지 않은 댓글인지 확인한다. */
function isOwner(comment: CommentResponse): boolean {
  return canWrite.value && !comment.isDeleted && comment.userId === props.loginUserId
}

/** 새 부모 댓글 행에 사용할 독립적인 대댓글 상태를 만든다. */
function createReplyState(): ReplyState {
  return reactive({
    open: false,
    loading: false,
    loaded: false,
    page: 1,
    size: 5,
    totalCount: 0,
    totalPages: 0,
    items: [],
    content: '',
    error: '',
  })
}

/**
 * 서버의 오류 메시지를 표시한다.
 * 세션이 만료되면 이 컴포넌트와 부모 화면의 쓰기 기능을 중지한다.
 */
function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    sessionExpired.value = true
    emit('session-expired')
  }

  return error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

/** 서버와 동일하게 공백뿐인 본문과 2,000자 초과 입력을 차단한다. */
function validateContent(value: string): boolean {
  if (!value.trim()) {
    actionError.value = '내용을 입력해 주세요.'
    return false
  }

  if (value.length > 2000) {
    actionError.value = '내용은 2,000자까지 입력할 수 있습니다.'
    return false
  }

  return true
}

/** 새 변경 요청을 시작하기 전에 이전 안내를 초기화한다. */
function clearActionMessages() {
  actionError.value = ''
  successMessage.value = ''
}

/** 수정 입력을 닫고 기존 입력값을 비운다. */
function cancelEdit() {
  if (saving.value) return
  editingId.value = null
  editingContent.value = ''
}

/**
 * 일반 댓글 목록을 조회한다.
 * 부모 댓글 목록이 바뀌면 기존 대댓글 펼침·입력 상태는 초기화한다.
 */
async function loadComments(targetPage = page.value) {
  if (loading.value) return

  loading.value = true
  listError.value = ''

  try {
    let result = await fetchComments(props.communityId, targetPage, size.value)

    // 게시글의 데이터 변화로 현재 페이지가 없어졌다면 마지막 페이지를 조회한다.
    const lastPage = Math.max(1, result.totalPages)

    if (targetPage > lastPage) {
      result = await fetchComments(props.communityId, lastPage, size.value)
    }

    rows.value = result.items.map((comment) => ({
      comment,
      replies: createReplyState(),
    }))

    applyPage(result)
    editingId.value = null
    editingContent.value = ''
  } catch (error: unknown) {
    listError.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/** 일반 댓글의 페이지를 이동한다. */
async function changePage(targetPage: number) {
  if (busy.value || !canMoveTo(targetPage)) return
  await loadComments(targetPage)
}

/**
 * 특정 부모 댓글의 대댓글만 조회한다.
 * 다른 부모의 대댓글 목록이나 페이지에는 영향을 주지 않는다.
 */
async function loadReplies(row: CommentRow, targetPage = row.replies.page) {
  const state = row.replies
  if (state.loading) return

  state.loading = true
  state.error = ''

  try {
    let result = await fetchReplies(props.communityId, row.comment.id, targetPage, state.size)

    const lastPage = Math.max(1, result.totalPages)

    if (targetPage > lastPage) {
      result = await fetchReplies(props.communityId, row.comment.id, lastPage, state.size)
    }

    state.items = result.items
    state.page = result.page
    state.size = result.size
    state.totalCount = result.totalCount
    state.totalPages = result.totalPages
    state.loaded = true
  } catch (error: unknown) {
    state.error = getErrorMessage(error)
  } finally {
    state.loading = false
  }
}

/** 대댓글 영역을 펼칠 때 최초 한 번 조회한다. */
async function toggleReplies(row: CommentRow) {
  if (busy.value || row.replies.loading) return

  row.replies.open = !row.replies.open

  if (row.replies.open && !row.replies.loaded) {
    await loadReplies(row, 1)
  }
}

/** 선택한 부모 댓글의 대댓글 페이지를 이동한다. */
async function changeReplyPage(row: CommentRow, targetPage: number) {
  const state = row.replies

  if (busy.value || state.loading) return
  if (!Number.isInteger(targetPage)) return
  if (targetPage < 1 || targetPage > state.totalPages) return
  if (targetPage === state.page) return

  // 다른 페이지로 이동하면 현재 수정 입력은 닫는다.
  cancelEdit()
  await loadReplies(row, targetPage)
}

/**
 * 일반 댓글을 등록한다.
 * 작성순 정렬이므로 새 댓글을 확인할 수 있도록 마지막 페이지를 조회한다.
 */
async function submitComment() {
  if (busy.value || !canWrite.value) return

  clearActionMessages()
  if (!validateContent(content.value)) return

  saving.value = true

  try {
    await createComment(props.communityId, {
      parentId: null,
      content: content.value,
    })

    // 저장 성공 후 입력을 비워 재등록을 방지한다.
    content.value = ''
    successMessage.value = '댓글을 등록했습니다.'

    // 서버의 최신 건수를 조회해 마지막 페이지를 계산한다.
    // 목록 재조회 실패를 등록 실패로 안내하지 않도록 따로 처리한다.
    try {
      const latest = await fetchComments(props.communityId, 1, size.value)
      await loadComments(Math.max(1, latest.totalPages))
    } catch (error: unknown) {
      listError.value = getErrorMessage(error)
    }
  } catch (error: unknown) {
    actionError.value = getErrorMessage(error)
  } finally {
    saving.value = false
  }
}

/**
 * 일반 댓글에 대댓글을 등록한다.
 * 삭제된 부모 댓글에는 등록할 수 없다.
 */
async function submitReply(row: CommentRow) {
  if (busy.value || row.replies.loading || !canWrite.value) return
  if (row.comment.isDeleted) return

  clearActionMessages()
  if (!validateContent(row.replies.content)) return

  saving.value = true

  try {
    await createComment(props.communityId, {
      parentId: row.comment.id,
      content: row.replies.content,
    })

    row.replies.content = ''
    successMessage.value = '대댓글을 등록했습니다.'

    try {
      const latest = await fetchReplies(props.communityId, row.comment.id, 1, row.replies.size)

      await loadReplies(row, Math.max(1, latest.totalPages))
    } catch (error: unknown) {
      row.replies.error = getErrorMessage(error)
    }
  } catch (error: unknown) {
    actionError.value = getErrorMessage(error)
  } finally {
    saving.value = false
  }
}

/** 본인 댓글의 현재 본문을 수정 입력창에 복사한다. */
function startEdit(comment: CommentResponse) {
  if (busy.value || !isOwner(comment)) return

  clearActionMessages()
  editingId.value = comment.id
  editingContent.value = comment.content
}

/**
 * 수정 성공 후 해당 목록만 다시 조회한다.
 * 대댓글 수정은 부모의 대댓글 목록만 갱신한다.
 */
async function saveEdit(comment: CommentResponse, row: CommentRow) {
  if (busy.value || row.replies.loading || !isOwner(comment)) return

  clearActionMessages()
  if (!validateContent(editingContent.value)) return

  saving.value = true

  try {
    await updateComment(props.communityId, comment.id, {
      content: editingContent.value,
    })

    editingId.value = null
    editingContent.value = ''
    successMessage.value = '댓글을 수정했습니다.'

    if (comment.parentId === null) {
      await loadComments()
    } else {
      await loadReplies(row)
    }
  } catch (error: unknown) {
    actionError.value = getErrorMessage(error)
  } finally {
    saving.value = false
  }
}

/**
 * 삭제 확인 후 논리 삭제한다.
 * 부모 댓글을 삭제해도 기존 대댓글은 서버에서 유지된다.
 */
async function removeComment(comment: CommentResponse, row: CommentRow) {
  if (busy.value || row.replies.loading || !isOwner(comment)) return
  if (!window.confirm('댓글을 삭제할까요?')) return

  clearActionMessages()
  saving.value = true

  try {
    await deleteComment(props.communityId, comment.id)

    editingId.value = null
    editingContent.value = ''
    successMessage.value = '댓글을 삭제했습니다.'

    if (comment.parentId === null) {
      await loadComments()
    } else {
      await loadReplies(row)
    }
  } catch (error: unknown) {
    actionError.value = getErrorMessage(error)
  } finally {
    saving.value = false
  }
}

/** DB의 UTC 일시를 한국 시간으로 표시한다. */
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

/** 부모 화면에서 게시글을 정상 조회한 후 컴포넌트가 생성된다. */
onMounted(() => loadComments(1))
</script>

<template>
  <section class="card comment-section" aria-label="댓글">
    <div class="card-heading">
      <h2>댓글</h2>
      <!-- 일반 댓글 건수이며 대댓글 수는 포함하지 않는다. -->
      <span class="comment-count"> 일반 댓글 {{ totalCount.toLocaleString('ko-KR') }}건 </span>
    </div>

    <!-- 일반 댓글 작성 -->
    <form v-if="canWrite" class="comment-form" @submit.prevent="submitComment">
      <label for="new-comment">댓글 작성</label>
      <textarea
        id="new-comment"
        v-model="content"
        rows="3"
        maxlength="2000"
        placeholder="댓글을 입력해 주세요."
        :disabled="busy"
      ></textarea>

      <div class="form-bottom">
        <span class="input-count">{{ content.length }} / 2,000자</span>
        <button type="submit" class="btn btn-primary" :disabled="busy || !content.trim()">
          댓글 등록
        </button>
      </div>
    </form>

    <p v-else class="comment-help">
      {{ authError || '댓글 작성은 로그인 후 이용할 수 있습니다.' }}
    </p>

    <!-- 저장 성공과 목록 조회 실패를 별도로 표시한다. -->
    <p v-if="successMessage" class="comment-success" role="status">
      {{ successMessage }}
    </p>
    <p v-if="actionError" class="message error-message" role="alert">
      {{ actionError }}
    </p>

    <div v-if="listError" class="comment-error">
      <p class="message error-message" role="alert">{{ listError }}</p>
      <button type="button" class="btn btn-secondary" :disabled="busy" @click="loadComments()">
        다시 조회
      </button>
    </div>

    <p v-if="loading" class="empty-state" role="status">댓글을 불러오는 중입니다.</p>

    <template v-else-if="!listError">
      <p v-if="rows.length === 0" class="empty-state">아직 등록된 댓글이 없습니다.</p>

      <ul v-else class="comment-list">
        <li v-for="row in rows" :key="row.comment.id" class="comment-item">
          <div class="comment-meta">
            <strong>
              {{ row.comment.isDeleted ? '삭제된 댓글' : row.comment.nickname }}
            </strong>
            <span>{{ formatDateTime(row.comment.createdDt) }}</span>
            <span v-if="!row.comment.isDeleted && row.comment.updatedDt !== row.comment.createdDt">
              수정됨
            </span>
          </div>

          <!-- 일반 댓글 수정 -->
          <form
            v-if="editingId === row.comment.id"
            class="comment-form edit-form"
            @submit.prevent="saveEdit(row.comment, row)"
          >
            <label :for="`edit-comment-${row.comment.id}`">댓글 수정</label>
            <textarea
              :id="`edit-comment-${row.comment.id}`"
              v-model="editingContent"
              rows="3"
              maxlength="2000"
              :disabled="busy"
            ></textarea>

            <div class="comment-actions">
              <button type="button" class="btn btn-secondary" :disabled="busy" @click="cancelEdit">
                취소
              </button>
              <button
                type="submit"
                class="btn btn-primary"
                :disabled="busy || !editingContent.trim()"
              >
                저장
              </button>
            </div>
          </form>

          <p v-else class="comment-content" :class="{ 'deleted-content': row.comment.isDeleted }">
            {{ row.comment.content }}
          </p>

          <div class="comment-actions">
            <!-- 삭제된 부모도 기존 대댓글 조회는 허용한다. -->
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="busy || row.replies.loading"
              :aria-expanded="row.replies.open"
              :aria-controls="`replies-${row.comment.id}`"
              @click="toggleReplies(row)"
            >
              {{ row.replies.open ? '답글 닫기' : '답글 보기' }}
            </button>

            <template v-if="isOwner(row.comment) && editingId !== row.comment.id">
              <button
                type="button"
                class="btn btn-edit"
                :disabled="busy || row.replies.loading"
                @click="startEdit(row.comment)"
              >
                수정
              </button>
              <button
                type="button"
                class="btn btn-delete"
                :disabled="busy || row.replies.loading"
                @click="removeComment(row.comment, row)"
              >
                삭제
              </button>
            </template>
          </div>

          <!-- 부모 댓글별 독립적인 대댓글 영역 -->
          <section
            v-if="row.replies.open"
            :id="`replies-${row.comment.id}`"
            class="reply-section"
            aria-label="대댓글"
          >
            <h3>답글 {{ row.replies.totalCount }}건</h3>

            <p v-if="row.replies.loading" class="comment-help" role="status">
              답글을 불러오는 중입니다.
            </p>

            <div v-else-if="row.replies.error">
              <p class="message error-message" role="alert">
                {{ row.replies.error }}
              </p>
              <button
                type="button"
                class="btn btn-secondary"
                :disabled="busy"
                @click="loadReplies(row)"
              >
                다시 조회
              </button>
            </div>

            <template v-else>
              <p v-if="row.replies.items.length === 0" class="comment-help">
                등록된 답글이 없습니다.
              </p>

              <ul v-else class="comment-list">
                <li v-for="reply in row.replies.items" :key="reply.id" class="reply-item">
                  <div class="comment-meta">
                    <strong>
                      {{ reply.isDeleted ? '삭제된 댓글' : reply.nickname }}
                    </strong>
                    <span>{{ formatDateTime(reply.createdDt) }}</span>
                    <span v-if="!reply.isDeleted && reply.updatedDt !== reply.createdDt">
                      수정됨
                    </span>
                  </div>

                  <form
                    v-if="editingId === reply.id"
                    class="comment-form edit-form"
                    @submit.prevent="saveEdit(reply, row)"
                  >
                    <label :for="`edit-reply-${reply.id}`">답글 수정</label>
                    <textarea
                      :id="`edit-reply-${reply.id}`"
                      v-model="editingContent"
                      rows="3"
                      maxlength="2000"
                      :disabled="busy"
                    ></textarea>

                    <div class="comment-actions">
                      <button
                        type="button"
                        class="btn btn-secondary"
                        :disabled="busy"
                        @click="cancelEdit"
                      >
                        취소
                      </button>
                      <button
                        type="submit"
                        class="btn btn-primary"
                        :disabled="busy || !editingContent.trim()"
                      >
                        저장
                      </button>
                    </div>
                  </form>

                  <p v-else class="comment-content" :class="{ 'deleted-content': reply.isDeleted }">
                    {{ reply.content }}
                  </p>

                  <!-- 대댓글에는 추가 답글 버튼을 제공하지 않는다. -->
                  <div v-if="isOwner(reply) && editingId !== reply.id" class="comment-actions">
                    <button
                      type="button"
                      class="btn btn-edit"
                      :disabled="busy"
                      @click="startEdit(reply)"
                    >
                      수정
                    </button>
                    <button
                      type="button"
                      class="btn btn-delete"
                      :disabled="busy"
                      @click="removeComment(reply, row)"
                    >
                      삭제
                    </button>
                  </div>
                </li>
              </ul>

              <Pagination
                :page="row.replies.page"
                :total-pages="row.replies.totalPages"
                :disabled="busy || row.replies.loading"
                @change="changeReplyPage(row, $event)"
              />
            </template>

            <!-- 삭제된 부모에는 새 대댓글을 작성할 수 없다. -->
            <form
              v-if="canWrite && !row.comment.isDeleted"
              class="comment-form reply-form"
              @submit.prevent="submitReply(row)"
            >
              <label :for="`new-reply-${row.comment.id}`">답글 작성</label>
              <textarea
                :id="`new-reply-${row.comment.id}`"
                v-model="row.replies.content"
                rows="2"
                maxlength="2000"
                placeholder="답글을 입력해 주세요."
                :disabled="busy || row.replies.loading"
              ></textarea>

              <div class="form-bottom">
                <span class="input-count"> {{ row.replies.content.length }} / 2,000자 </span>
                <button
                  type="submit"
                  class="btn btn-primary"
                  :disabled="busy || row.replies.loading || !row.replies.content.trim()"
                >
                  답글 등록
                </button>
              </div>
            </form>
          </section>
        </li>
      </ul>

      <Pagination :page="page" :total-pages="totalPages" :disabled="busy" @change="changePage" />
    </template>
  </section>
</template>

<style scoped>
/* 기존 카드 스타일을 유지하며 게시글 카드와 간격을 둔다. */
.comment-section {
  margin-top: 24px;
}

.comment-count,
.comment-help,
.input-count {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.comment-form {
  display: grid;
  gap: 10px;
}

.comment-form label {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.comment-form textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  font: inherit;
  line-height: 1.6;
  resize: vertical;
}

.comment-form textarea:focus {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.comment-form textarea:disabled {
  background: #f8fafc;
}

.form-bottom,
.comment-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.form-bottom {
  justify-content: space-between;
}

.comment-actions {
  justify-content: flex-end;
}

.comment-list {
  padding: 0;
  margin: 20px 0 0;
  list-style: none;
}

.comment-item {
  padding: 20px 0;
  border-top: 1px solid #e2e8f0;
}

.comment-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #64748b;
  font-size: 12px;
}

.comment-meta strong {
  color: #334155;
  font-size: 14px;
}

/* HTML로 렌더링하지 않고 입력한 줄바꿈과 긴 문자열을 안전하게 표시한다. */
.comment-content {
  margin: 12px 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.deleted-content {
  color: #94a3b8;
}

.edit-form {
  margin: 12px 0;
}

/* 답글 영역은 배경과 들여쓰기로 부모 댓글과 구분한다. */
.reply-section {
  margin: 16px 0 0 20px;
  padding: 16px;
  border-left: 3px solid #dbeafe;
  border-radius: 8px;
  background: #f8fafc;
}

.reply-section h3 {
  margin: 0 0 12px;
  color: #334155;
  font-size: 14px;
}

.reply-section .comment-list {
  margin-top: 0;
}

.reply-item {
  padding: 14px 0;
  border-bottom: 1px solid #e2e8f0;
}

.reply-form {
  margin-top: 20px;
}

.comment-success {
  padding: 12px 16px;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  font-size: 14px;
}

.comment-error {
  margin-top: 16px;
}

@media (max-width: 560px) {
  .reply-section {
    margin-left: 8px;
    padding: 12px;
  }

  .comment-actions .btn,
  .form-bottom .btn {
    min-height: 40px;
  }
}
</style>
