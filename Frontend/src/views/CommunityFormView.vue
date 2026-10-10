<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Pencil, Save } from '@lucide/vue'
import { ApiError, request } from '../api/client'

interface CommunityDetail {
  id: number
  userId: number
  title: string
  content: string
}

const route = useRoute()
const router = useRouter()
const baseUrl = '/api/community'

// 수정 대상이 없으면 등록 모드다.
const editingId = ref<number | null>(null)
const isEdit = computed(() => editingId.value !== null)

const form = reactive({
  title: '',
  content: '',
})

const loading = ref(true)
const saving = ref(false)
// 로그인 확인과 수정 데이터 준비가 끝난 경우에만 저장을 허용한다.
// 준비 실패 또는 저장 완료 후에는 false로 유지해 잘못된 제출을 막는다.
const ready = ref(false)
const loginRequired = ref(false)
const errorMessage = ref('')

// 로그인 확인 후 등록 또는 수정 폼을 준비한다.
async function loadForm(rawId: unknown) {
  loading.value = true
  ready.value = false
  loginRequired.value = false
  errorMessage.value = ''
  editingId.value = null
  form.title = ''
  form.content = ''

  try {
    // URL에 ID가 있으면 수정 모드이며, 올바른 번호인지 검사한다.
    if (rawId !== undefined) {
      const id = Number(rawId)

      if (!Number.isSafeInteger(id) || id <= 0) {
        throw new Error('올바르지 않은 게시글 번호입니다.')
      }

      editingId.value = id
    }

    const user = await request<{ id: number }>('/api/auth/me')

    if (user === null) {
      throw new Error('로그인 사용자 정보가 없습니다.')
    }

    if (editingId.value !== null) {
      // 수정 전용 API를 사용해 폼을 여는 것만으로 조회수가 증가하지 않도록 한다.
      const detail = await request<CommunityDetail>(`${baseUrl}/${editingId.value}/edit`)

      if (detail === null) {
        throw new Error('게시글 상세 응답이 없습니다.')
      }

      // 화면에서도 확인하지만 실제 수정 권한은 서버가 최종 검사한다.
      if (detail.userId !== user.id) {
        throw new Error('본인이 작성한 게시글만 수정할 수 있습니다.')
      }

      form.title = detail.title
      form.content = detail.content
    }

    ready.value = true
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      loginRequired.value = true
      errorMessage.value = '글을 작성하거나 수정하려면 로그인해 주세요.'
    } else {
      errorMessage.value = error instanceof Error ? error.message : '화면을 불러오지 못했습니다.'
    }
  } finally {
    loading.value = false
  }
}

// 등록은 POST, 수정은 PUT으로 제목과 본문을 전달한다.
async function saveCommunity() {
  if (loading.value || saving.value || !ready.value) return

  errorMessage.value = ''

  const title = form.title.trim()
  const content = form.content.trim()

  if (!title || !content) {
    errorMessage.value = '제목과 내용을 입력해 주세요.'
    return
  }

  if (title.length > 200 || content.length > 10000) {
    errorMessage.value = '제목은 200자, 내용은 10,000자 이내로 입력해 주세요.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    // CSRF 토큰 처리는 공통 request에서 수행한다.
    await request<null>(id === null ? baseUrl : `${baseUrl}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, content }),
    })

    // 저장 성공 후 재전송을 막는다.
    ready.value = false

    // 등록 응답에는 ID가 없으므로 목록으로, 수정 후에는 상세로 이동한다.
    await router.replace(id === null ? '/community' : `/community/${id}`)
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      loginRequired.value = true
      ready.value = false
    }

    errorMessage.value = error instanceof Error ? error.message : '게시글 저장에 실패했습니다.'
  } finally {
    saving.value = false
  }
}

// 취소 시 수정은 상세 화면으로, 등록은 목록으로 돌아간다.
function cancelForm() {
  if (saving.value) return

  void router.push(editingId.value === null ? '/community' : `/community/${editingId.value}`)
}

// 같은 폼 컴포넌트에서 등록·수정 경로가 바뀌어도 새로 준비한다.
onBeforeRouteUpdate(async (to) => {
  // 조회·저장 중에는 같은 폼의 다른 경로로 전환하지 않아 입력 상태가 섞이지 않게 한다.
  if (saving.value || loading.value) return false

  await loadForm(to.params.id)
})

onMounted(() => loadForm(route.params.id))
</script>

<template>
  <section class="crud-page community-form-page">
    <header class="page-heading">
      <div class="page-icon">
        <Pencil :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>{{ isEdit ? '게시글 수정' : '글쓰기' }}</h1>
        <p>자취 생활의 이야기와 유용한 정보를 나눠보세요.</p>
      </div>
    </header>

    <p v-if="loading" class="empty-state" role="status">화면을 준비하고 있습니다.</p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <!-- 로그인하지 않았거나 폼 준비에 실패한 경우 -->
    <div v-if="!loading && !ready" class="form-navigation">
      <RouterLink v-if="loginRequired" to="/login" class="btn btn-primary"> 로그인 </RouterLink>
      <RouterLink to="/community" class="btn btn-secondary"> 목록으로 </RouterLink>
    </div>

    <form v-if="!loading && ready" class="card" @submit.prevent="saveCommunity">
      <div class="card-heading">
        <div class="heading-title">
          <Pencil :size="20" aria-hidden="true" />
          <h2>{{ isEdit ? '게시글 수정' : '새 게시글' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving">
        <legend class="sr-only">게시글 입력</legend>

        <div class="form-grid post-fields">
          <label>
            제목 *
            <input
              v-model="form.title"
              maxlength="200"
              placeholder="제목을 입력해 주세요"
              required
            />
            <span class="character-count">{{ form.title.length }} / 200</span>
          </label>

          <label>
            내용 *
            <textarea
              v-model="form.content"
              maxlength="10000"
              rows="14"
              placeholder="내용을 입력해 주세요"
              required
            ></textarea>
            <span class="character-count">
              {{ form.content.length.toLocaleString('ko-KR') }} / 10,000
            </span>
          </label>
        </div>

        <div class="post-form-actions">
          <button type="button" class="btn btn-secondary" @click="cancelForm">취소</button>

          <button type="submit" class="btn btn-primary">
            <Save :size="16" aria-hidden="true" />
            {{ saving ? '저장 중…' : isEdit ? '수정 저장' : '등록' }}
          </button>
        </div>
      </fieldset>
    </form>
  </section>
</template>

<style scoped>
/* 제목과 본문을 위아래로 배치한다. */
.community-form-page .post-fields {
  grid-template-columns: minmax(0, 1fr);
}

.post-fields textarea {
  min-height: 300px;
  resize: vertical;
  line-height: 1.7;
}

.character-count {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
  text-align: right;
}

.post-form-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
}

.form-navigation {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.form-navigation a {
  text-decoration: none;
}
</style>
