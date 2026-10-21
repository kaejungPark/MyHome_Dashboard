<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserRound, KeyRound } from '@lucide/vue'
import { ApiError, request } from '../api/client'
import { useErrorMessage } from '../composables/useErrorMessage'

type Gender = 'MALE' | 'FEMALE'

// 서버에서 조회하는 본인의 회원 정보다.
interface ProfileResponse {
  email: string
  name: string | null
  nickname: string
  age: number | null
  gender: Gender | null
}

const router = useRouter()

const loading = ref(false)
const loaded = ref(false)
const saving = ref(false)
const notice = ref('')

const { errorMessage, showError } = useErrorMessage('회원 정보를 처리하지 못했습니다.')

// 이메일은 조회만 가능하며 수정 요청에 포함하지 않는다.
const form = reactive({
  email: '',
  name: '',
  nickname: '',
  age: '' as number | '',
  gender: '' as Gender | '',
})

// 비밀번호 변경은 회원 정보 수정과 별도의 상태로 관리한다.
const passwordDialog = ref<HTMLDialogElement | null>(null)
const changingPassword = ref(false)
const passwordChanged = ref(false)

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  passwordConfirm: '',
})

const { errorMessage: passwordError, showError: showPasswordError } =
  useErrorMessage('비밀번호를 변경하지 못했습니다.')

const busy = computed(() => loading.value || saving.value || changingPassword.value)

// 서버 응답을 회원 정보 폼에 반영한다.
function applyProfile(profile: ProfileResponse) {
  form.email = profile.email
  form.name = profile.name ?? ''
  form.nickname = profile.nickname
  form.age = profile.age ?? ''
  form.gender = profile.gender ?? ''
}

/**
 * DB에 저장된 본인의 회원 정보를 조회한다.
 * 세션이 만료된 경우 로그인 화면으로 이동한다.
 */
async function loadData() {
  if (busy.value) return

  loading.value = true
  loaded.value = false
  errorMessage.value = ''

  try {
    const result = await request<ProfileResponse>('/api/auth/profile')

    if (result === null) {
      throw new Error('회원 정보를 받지 못했습니다.')
    }

    applyProfile(result)
    loaded.value = true
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      window.location.replace('/login')
      return
    }

    showError(error)
  } finally {
    loading.value = false
  }
}

/**
 * 일반 회원 정보만 저장한다.
 * 비밀번호와 이메일은 이 요청에서 변경하지 않는다.
 */
async function saveProfile() {
  if (busy.value || !loaded.value) return

  errorMessage.value = ''
  notice.value = ''

  const nickname = form.nickname.trim()
  const name = form.name.trim()

  if (!nickname) {
    errorMessage.value = '닉네임을 입력해 주세요.'
    return
  }

  if (nickname.length > 100 || name.length > 100) {
    errorMessage.value = '이름과 닉네임은 각각 100자까지 입력할 수 있습니다.'
    return
  }

  if (form.age !== '' && (!Number.isInteger(form.age) || form.age < 0 || form.age > 120)) {
    errorMessage.value = '나이는 0~120 사이의 정수로 입력해 주세요.'
    return
  }

  saving.value = true

  try {
    await request<void>('/api/auth/profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: name || null,
        nickname,
        age: form.age === '' ? null : form.age,
        gender: form.gender === '' ? null : form.gender,
      }),
    })

    form.name = name
    form.nickname = nickname
    notice.value = '회원 정보를 수정했습니다.'

    // App.vue가 로그인 정보를 다시 조회하여 회원 카드의 닉네임을 갱신한다.
    window.dispatchEvent(new Event('profile-updated'))
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      window.location.replace('/login')
      return
    }

    showError(error)
  } finally {
    saving.value = false
  }
}

// 저장하지 않은 입력을 버리고 대시보드로 이동한다.
function cancelEdit() {
  if (busy.value) return

  void router.push('/')
}

// 비밀번호 입력값은 팝업을 열고 닫을 때 초기화한다.
function clearPasswordForm() {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.passwordConfirm = ''
  passwordError.value = ''
}

function openPasswordDialog() {
  if (busy.value || !loaded.value) return

  clearPasswordForm()
  passwordChanged.value = false
  passwordDialog.value?.showModal()
}

function closePasswordDialog() {
  if (changingPassword.value) return

  passwordDialog.value?.close()
  clearPasswordForm()
}

// Esc로 닫는 경우에도 변경 요청 중이면 닫기를 막는다.
function handlePasswordCancel(event: Event) {
  event.preventDefault()
  closePasswordDialog()
}

/**
 * 현재 비밀번호를 확인한 후 새 비밀번호로 변경한다.
 * 성공 응답은 서버의 비밀번호 저장 및 세션 종료 완료를 의미한다.
 */
async function changePassword() {
  if (changingPassword.value || passwordChanged.value) return

  passwordError.value = ''

  const { currentPassword, newPassword, passwordConfirm } = passwordForm

  if (!currentPassword) {
    passwordError.value = '현재 비밀번호를 입력해 주세요.'
    return
  }

  // 비밀번호는 공백도 값의 일부이므로 trim()하지 않는다.
  if (!newPassword.trim() || newPassword.length < 12) {
    passwordError.value = '새 비밀번호는 12자 이상 입력해 주세요.'
    return
  }

  // 기존 회원가입과 동일한 BCrypt 입력 길이 제한을 적용한다.
  if (new TextEncoder().encode(newPassword).length > 72) {
    passwordError.value = '새 비밀번호가 너무 깁니다. 조금 더 짧게 입력해 주세요.'
    return
  }

  if (currentPassword === newPassword) {
    passwordError.value = '현재 비밀번호와 다른 비밀번호를 입력해 주세요.'
    return
  }

  if (newPassword !== passwordConfirm) {
    passwordError.value = '새 비밀번호가 일치하지 않습니다.'
    return
  }

  changingPassword.value = true

  try {
    await request<void>('/api/auth/password', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        currentPassword,
        newPassword,
      }),
    })

    passwordChanged.value = true
    clearPasswordForm()
    passwordDialog.value?.close()

    window.alert('비밀번호를 변경했습니다. 새 비밀번호로 다시 로그인해 주세요.')

    // 앱에 남은 로그인 상태와 개인 데이터를 비우고 로그인 화면으로 이동한다.
    window.location.replace('/login')
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      window.location.replace('/login')
      return
    }

    showPasswordError(error)
  } finally {
    changingPassword.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <section class="crud-page profile-page" :aria-busy="busy">
    <header class="page-heading">
      <div class="page-icon">
        <UserRound :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>회원 정보 수정</h1>
        <p>내 회원 정보를 확인하고 수정합니다.</p>
      </div>
    </header>

    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <p v-if="loading" class="empty-state" role="status">회원 정보를 불러오는 중입니다.</p>

    <div v-else-if="!loaded">
      <button type="button" class="btn btn-secondary" @click="loadData">다시 조회</button>
    </div>

    <form v-else class="card profile-card" @submit.prevent="saveProfile">
      <div class="card-heading">
        <div class="heading-title">
          <UserRound :size="20" aria-hidden="true" />
          <h2>기본 정보</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="busy">
        <legend class="sr-only">회원 정보 입력</legend>

        <div class="form-grid">
          <label class="memo-field">
            로그인 계정
            <input :value="form.email" type="text" autocomplete="username" readonly />
            <span class="field-help"> 로그인 계정은 이 화면에서 변경할 수 없습니다. </span>
          </label>

          <label>
            이름 (선택)
            <input
              v-model="form.name"
              type="text"
              autocomplete="name"
              maxlength="100"
              placeholder="이름을 입력해 주세요"
            />
          </label>

          <label>
            닉네임 *
            <input
              v-model="form.nickname"
              type="text"
              autocomplete="nickname"
              maxlength="100"
              placeholder="화면에 표시할 이름"
              required
            />
          </label>

          <label>
            나이 (선택)
            <input
              v-model.number="form.age"
              type="number"
              min="0"
              max="120"
              step="1"
              placeholder="나이를 입력해 주세요"
            />
          </label>

          <label>
            성별 (선택)
            <select v-model="form.gender">
              <option value="">선택 안 함</option>
              <option value="MALE">남성</option>
              <option value="FEMALE">여성</option>
            </select>
          </label>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary">
            {{ saving ? '저장 중...' : '저장' }}
          </button>

          <button type="button" class="btn btn-secondary" @click="cancelEdit">취소</button>

          <button type="button" class="btn btn-secondary" @click="openPasswordDialog">
            <KeyRound :size="16" aria-hidden="true" />
            비밀번호 변경
          </button>
        </div>
      </fieldset>
    </form>

    <!-- 별도 팝업에서만 비밀번호를 변경한다. -->
    <dialog
      ref="passwordDialog"
      class="password-dialog"
      aria-labelledby="password-dialog-title"
      aria-describedby="password-dialog-description"
      :aria-busy="changingPassword"
      @cancel="handlePasswordCancel"
      @close="clearPasswordForm"
    >
      <h2 id="password-dialog-title">비밀번호 변경</h2>
      <p id="password-dialog-description" class="password-description">
        변경 후 로그아웃되며 새 비밀번호로 다시 로그인해야 합니다. 회원 정보 화면의 저장하지 않은
        내용은 반영되지 않습니다.
      </p>

      <form @submit.prevent="changePassword">
        <fieldset :disabled="changingPassword || passwordChanged">
          <legend class="sr-only">비밀번호 변경 정보</legend>

          <!-- 비밀번호 관리자에서 현재 계정을 식별할 수 있도록 제공한다. -->
          <input
            :value="form.email"
            type="text"
            name="username"
            autocomplete="username"
            class="sr-only"
            readonly
            tabindex="-1"
          />

          <div class="form-grid password-fields">
            <label>
              현재 비밀번호 *
              <input
                v-model="passwordForm.currentPassword"
                type="password"
                autocomplete="current-password"
                required
                autofocus
              />
            </label>

            <label>
              새 비밀번호 *
              <input
                v-model="passwordForm.newPassword"
                type="password"
                autocomplete="new-password"
                minlength="12"
                placeholder="12자 이상 입력해 주세요"
                required
              />
            </label>

            <label>
              새 비밀번호 확인 *
              <input
                v-model="passwordForm.passwordConfirm"
                type="password"
                autocomplete="new-password"
                minlength="12"
                placeholder="새 비밀번호를 다시 입력해 주세요"
                required
              />
            </label>
          </div>

          <p v-if="passwordError" class="message error-message password-error" role="alert">
            {{ passwordError }}
          </p>

          <div class="form-actions">
            <button type="button" class="btn btn-secondary" @click="closePasswordDialog">
              취소
            </button>

            <button type="submit" class="btn btn-primary">
              {{ changingPassword ? '변경 중...' : '비밀번호 변경' }}
            </button>
          </div>
        </fieldset>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.profile-card {
  max-width: 820px;
}

.field-help {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.6;
}

.profile-page input[readonly] {
  background-color: #f8fafc;
  color: #64748b;
}

/* 네이티브 dialog가 팝업 바깥의 조작을 막고 초점을 관리한다. */
.password-dialog {
  width: min(480px, calc(100% - 32px));
  max-height: calc(100dvh - 48px);
  margin: auto;
  padding: 28px;
  overflow-y: auto;
  border: 1px solid #dce3ed;
  border-radius: 16px;
  background-color: #fff;
  color: #1e293b;
  box-shadow: 0 20px 60px rgb(15 23 42 / 18%);
}

.password-dialog::backdrop {
  background-color: rgb(15 23 42 / 45%);
}

.password-dialog h2 {
  margin: 0 0 12px;
  font-size: 22px;
}

.password-description {
  margin: 0 0 24px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.password-fields {
  grid-template-columns: minmax(0, 1fr);
}

.password-error {
  margin-top: 16px;
}

@media (max-width: 560px) {
  .profile-page .page-heading h1 {
    font-size: 24px;
  }

  .password-dialog {
    padding: 20px;
  }
}
</style>
