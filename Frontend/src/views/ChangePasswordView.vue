<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { KeyRound, LockKeyhole } from '@lucide/vue'
import { ApiError, request } from '../api/client'

/**
 * 현재 로그인 상태를 확인하기 위한 서버 응답이다.
 *
 * 일반 로그인은 USER·ADMIN,
 * 임시 비밀번호 로그인은 PASSWORD_RESET 권한을 사용한다.
 */
interface LoginUser {
  id: number
  email: string
  nickname: string
  role: 'USER' | 'ADMIN' | 'PASSWORD_RESET'
  passwordChangeRequired: boolean
}

// 화면 진입 시 현재 로그인 정보를 확인하는 중인지 나타낸다.
const loading = ref(true)

// 서버에서 임시 로그인 상태를 확인한 경우에만 입력 폼을 표시한다.
const loaded = ref(false)

// 비밀번호 변경과 로그아웃 요청의 중복 실행을 방지한다.
const changingPassword = ref(false)
const loggingOut = ref(false)

// 변경 완료 후 화면 이동 전까지 추가 제출을 막는다.
const passwordChanged = ref(false)

// 변경 대상 계정을 화면에서 확인할 수 있도록 표시한다.
const accountEmail = ref('')

// 조회·검증·변경 중 발생한 오류를 화면에 표시한다.
const errorMessage = ref('')

// 세 입력란의 비밀번호 표시 여부를 함께 관리한다.
const showPassword = ref(false)

// 비밀번호는 이 화면의 메모리에서만 관리한다.
// URL이나 localStorage·sessionStorage에는 저장하지 않는다.
const form = reactive({
  currentPassword: '',
  newPassword: '',
  passwordConfirm: '',
})

// 서버 요청 중에는 입력과 다른 작업 버튼을 비활성화한다.
const busy = computed(
  () => loading.value || changingPassword.value || loggingOut.value || passwordChanged.value,
)

/**
 * 화면에 보관한 비밀번호 원문을 비운다.
 * 변경 완료 또는 로그아웃 후 호출한다.
 */
function clearPasswordForm() {
  form.currentPassword = ''
  form.newPassword = ''
  form.passwordConfirm = ''
  showPassword.value = false
}

/**
 * 임시 로그인 세션이 만료되거나 무효화된 경우 처리한다.
 *
 * 재발급·유효기간 만료·다른 요청의 비밀번호 변경 등으로
 * 서버가 401을 반환할 수 있다.
 */
function handleExpiredSession() {
  loaded.value = false
  clearPasswordForm()

  window.alert('로그인이 만료되었습니다. 로그인하거나 임시 비밀번호를 다시 발급받아 주세요.')

  // 앱을 다시 열어 App.vue에 남은 로그인 상태도 초기화한다.
  window.location.replace('/login')
}

/**
 * 현재 세션이 임시 비밀번호 로그인 상태인지 확인한다.
 *
 * 비로그인·만료된 세션: 로그인 화면으로 이동
 * 일반 로그인: 기존 회원 정보 수정 화면으로 이동
 * 임시 로그인: 이 화면에서 비밀번호 변경 허용
 *
 * 화면 진입 검사는 사용자 안내용이다.
 * 실제 API 접근 권한은 백엔드에서도 검사한다.
 */
async function loadLoginState() {
  loading.value = true
  loaded.value = false
  errorMessage.value = ''

  try {
    // 체험 데이터가 아닌 실제 서버 세션 정보를 조회한다.
    const user = await request<LoginUser>('/api/auth/me')

    if (!user || typeof user.passwordChangeRequired !== 'boolean') {
      throw new Error('로그인 정보를 확인하지 못했습니다.')
    }

    // 일반 로그인 사용자는 기존 회원 정보 화면의 변경 기능을 이용한다.
    if (!user.passwordChangeRequired) {
      window.location.replace('/profile')
      return
    }

    // 임시 로그인 상태와 권한이 서로 맞는지도 확인한다.
    if (user.role !== 'PASSWORD_RESET') {
      throw new Error('로그인 상태가 올바르지 않습니다. 다시 로그인해 주세요.')
    }

    accountEmail.value = user.email
    loaded.value = true
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      handleExpiredSession()
      return
    }

    // 통신 오류는 비로그인으로 단정하지 않고 재조회할 수 있게 한다.
    errorMessage.value =
      error instanceof Error ? error.message : '로그인 정보를 확인하지 못했습니다.'
  } finally {
    loading.value = false
  }
}

/**
 * 새 비밀번호를 저장하기 전에 입력값을 검사한다.
 *
 * 비밀번호의 앞뒤 공백도 값의 일부이므로 실제 값에는 trim하지 않는다.
 * trim()은 공백만 입력했는지 검사할 때만 사용한다.
 *
 * 문자 수 검사와 별도로 BCrypt의 UTF-8 72바이트 제한을 확인한다.
 */
function validatePassword(): boolean {
  const { currentPassword, newPassword, passwordConfirm } = form

  if (!currentPassword.trim()) {
    errorMessage.value = '이메일로 받은 임시 비밀번호를 입력해 주세요.'
    return false
  }

  if (!newPassword.trim() || newPassword.length < 12 || newPassword.length > 72) {
    errorMessage.value = '새 비밀번호는 12~72자 사이로 입력해 주세요.'
    return false
  }

  const encoder = new TextEncoder()

  if (encoder.encode(currentPassword).length > 72) {
    errorMessage.value = '임시 비밀번호가 너무 깁니다. 입력값을 확인해 주세요.'
    return false
  }

  if (encoder.encode(newPassword).length > 72) {
    errorMessage.value = '새 비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요.'
    return false
  }

  if (currentPassword === newPassword) {
    errorMessage.value = '임시 비밀번호와 다른 새 비밀번호를 입력해 주세요.'
    return false
  }

  if (newPassword !== passwordConfirm) {
    errorMessage.value = '새 비밀번호와 확인 값이 일치하지 않습니다.'
    return false
  }

  // 기존 일반 비밀번호와 같은지는 서버에서 저장된 해시로 검사한다.
  return true
}

/**
 * 임시 비밀번호를 확인하고 새 비밀번호로 변경한다.
 *
 * 요청 본문:
 * - currentPassword: 이메일로 받은 임시 비밀번호
 * - newPassword: 앞으로 사용할 새 비밀번호
 *
 * 비밀번호 확인 값은 화면 검증용이므로 서버로 보내지 않는다.
 * request()가 변경 요청 전에 새 CSRF 토큰을 자동 조회한다.
 */
async function changePassword() {
  if (busy.value || !loaded.value) return

  errorMessage.value = ''

  if (!validatePassword()) return

  changingPassword.value = true

  try {
    await request<void>('/api/auth/password', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      }),
    })

    /*
     * 이 API의 정상 응답은 data가 null이다.
     * 반환값의 존재 여부로 성공을 판단하지 않는다.
     *
     * 예외 없이 완료되면 서버에서 비밀번호 저장,
     * 임시 비밀번호 사용 처리, 현재 세션 종료가 완료된 상태다.
     */
    passwordChanged.value = true
    clearPasswordForm()

    window.alert('비밀번호를 변경했습니다. 새 비밀번호로 다시 로그인해 주세요.')

    // 앱에 남은 임시 로그인 상태를 비우고 로그인 화면으로 이동한다.
    window.location.replace('/login')
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      handleExpiredSession()
      return
    }

    // 비밀번호 불일치·재사용 등의 서버 검증 메시지를 표시한다.
    errorMessage.value = error instanceof Error ? error.message : '비밀번호를 변경하지 못했습니다.'
  } finally {
    changingPassword.value = false
  }
}

/**
 * 변경을 취소하면 임시 로그인 세션도 종료한다.
 *
 * 화면만 로그인 페이지로 이동하면 서버에는 임시 세션이 남으므로
 * 로그아웃 API가 정상 처리된 후 이동한다.
 */
async function cancelAndLogout() {
  if (busy.value) return

  loggingOut.value = true
  errorMessage.value = ''

  try {
    await request<void>('/api/auth/logout', {
      method: 'POST',
    })

    clearPasswordForm()
    window.location.replace('/login')
  } catch (error: unknown) {
    // 세션이 이미 없으면 추가 로그아웃 없이 로그인 화면으로 이동한다.
    if (error instanceof ApiError && error.status === 401) {
      clearPasswordForm()
      window.location.replace('/login')
      return
    }

    errorMessage.value =
      error instanceof Error ? error.message : '로그아웃하지 못했습니다. 다시 시도해 주세요.'
  } finally {
    loggingOut.value = false
  }
}

/**
 * 비밀번호 변경·로그아웃 요청 도중 다른 Vue 화면으로 이동하지 못하게 한다.
 * 새로고침이나 브라우저 창 닫기까지 막는 기능은 아니다.
 */
onBeforeRouteLeave(() => {
  if (changingPassword.value || loggingOut.value) {
    return false
  }

  clearPasswordForm()
  return true
})

// 화면에 진입하면 서버에서 임시 로그인 상태부터 확인한다.
onMounted(() => {
  void loadLoginState()
})
</script>

<template>
  <div class="crud-page change-password-page">
    <section class="card change-password-card" aria-labelledby="change-password-heading">
      <!-- 로그인·비밀번호 찾기 화면과 같은 인증 카드 형태다. -->
      <header class="change-password-heading">
        <div class="change-password-symbol">
          <KeyRound :size="30" aria-hidden="true" />
        </div>

        <h1 id="change-password-heading">새 비밀번호 설정</h1>

        <p>임시 비밀번호로 로그인했습니다. 서비스를 이용하려면 새 비밀번호로 변경해 주세요.</p>
      </header>

      <!-- 서버 확인 전에는 비밀번호 입력 폼을 표시하지 않는다. -->
      <p v-if="loading" class="loading-message" role="status">로그인 정보를 확인하고 있습니다.</p>

      <!-- 로그인 상태 조회와 비밀번호 변경 오류를 공통 영역에 표시한다. -->
      <p v-if="errorMessage" class="error-message" role="alert">
        {{ errorMessage }}
      </p>

      <!-- 통신 오류 등으로 진입 검사가 실패하면 다시 확인할 수 있다. -->
      <div v-if="!loading && !loaded" class="retry-actions">
        <button type="button" class="btn btn-secondary" :disabled="busy" @click="loadLoginState">
          다시 확인
        </button>
      </div>

      <!-- 임시 로그인 상태가 확인된 경우에만 변경 폼을 표시한다. -->
      <form v-if="loaded" :aria-busy="changingPassword" @submit.prevent="changePassword">
        <p class="account-email">
          {{ accountEmail }}
        </p>

        <!-- 요청 중에는 입력란 전체를 비활성화한다. -->
        <fieldset class="password-fieldset" :disabled="busy">
          <legend class="sr-only">새 비밀번호 설정 정보</legend>

          <div class="form-grid password-fields">
            <label for="temporary-password">
              임시 비밀번호 *

              <input
                id="temporary-password"
                v-model="form.currentPassword"
                name="currentPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="이메일로 받은 임시 비밀번호"
                maxlength="72"
                required
              />
            </label>

            <label for="new-password">
              새 비밀번호 *

              <input
                id="new-password"
                v-model="form.newPassword"
                name="newPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="12자 이상 입력해 주세요"
                minlength="12"
                maxlength="72"
                aria-describedby="new-password-help"
                required
              />
            </label>

            <label for="new-password-confirm">
              새 비밀번호 확인 *

              <input
                id="new-password-confirm"
                v-model="form.passwordConfirm"
                name="passwordConfirm"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="새 비밀번호를 다시 입력해 주세요"
                minlength="12"
                maxlength="72"
                required
              />
            </label>
          </div>

          <!-- 세 입력란의 표시 여부를 한 번에 변경한다. -->
          <label class="show-password-option">
            <input v-model="showPassword" type="checkbox" />
            <span>비밀번호 표시</span>
          </label>
        </fieldset>

        <p id="new-password-help" class="field-help">
          새 비밀번호는 12자 이상, UTF-8 기준 72바이트 이내로 입력해 주세요. 기존 비밀번호 및 임시
          비밀번호와 다르게 설정해야 합니다.
        </p>

        <div class="password-actions">
          <button type="submit" class="btn btn-primary" :disabled="busy">
            <LockKeyhole :size="18" aria-hidden="true" />
            {{ changingPassword ? '변경 중...' : '비밀번호 변경' }}
          </button>
        </div>
      </form>

      <!-- 취소 시 서버의 임시 로그인 세션도 함께 종료한다. -->
      <button
        v-if="!loading"
        type="button"
        class="cancel-button"
        :disabled="busy"
        @click="cancelAndLogout"
      >
        {{ loggingOut ? '로그아웃 중...' : '취소하고 로그아웃' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
/* 기존 로그인·비밀번호 찾기와 같은 중앙 카드 배치를 사용한다. */
.change-password-page {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.change-password-page .change-password-card {
  width: 100%;
  max-width: 480px;
  min-width: 0;
  margin: 0;
  padding: 36px;
  border-radius: 20px;
  box-shadow: 0 8px 28px rgb(30 41 59 / 4%);
}

/* 제목과 아이콘 */
.change-password-heading {
  margin-bottom: 28px;
  text-align: center;
}

.change-password-symbol {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  border-radius: 18px;
  background: #eaf1ff;
  color: #2563eb;
}

.change-password-heading h1 {
  margin: 0 0 8px;
  font-size: 26px;
}

.change-password-heading > p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
  word-break: keep-all;
}

/* 로그인 상태와 오류 안내 */
.loading-message {
  color: #64748b;
  font-size: 14px;
  text-align: center;
}

.error-message {
  margin: 0 0 20px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.retry-actions {
  display: flex;
  justify-content: center;
}

.account-email {
  margin: 0 0 24px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  text-align: center;
  overflow-wrap: anywhere;
}

/* fieldset 기본 테두리를 제거하고 공통 입력 스타일을 사용한다. */
.password-fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.password-fields {
  grid-template-columns: minmax(0, 1fr);
}

/* 비밀번호 표시 체크박스는 일반 텍스트 입력 스타일과 분리한다. */
.show-password-option {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
}

.show-password-option input {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: #2563eb;
}

.field-help {
  margin: 16px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

/* 저장 버튼과 취소 버튼 */
.password-actions {
  margin-top: 24px;
}

.password-actions .btn {
  width: 100%;
  min-height: 48px;
  font-size: 15px;
}

.cancel-button {
  display: block;
  width: 100%;
  margin-top: 24px;
  padding: 16px 0 0;
  border: 0;
  border-top: 1px solid #eef2f7;
  border-radius: 0;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
}

.cancel-button:hover:not(:disabled) {
  color: #2563eb;
}

.cancel-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* 스크린 리더에는 전달하되 화면에서는 숨긴다. */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* 모바일에서는 카드 여백을 줄인다. */
@media (max-width: 480px) {
  .change-password-page {
    padding: 16px 0;
  }

  .change-password-page .change-password-card {
    padding: 28px 20px;
  }

  .change-password-heading h1 {
    font-size: 24px;
  }
}
</style>
