<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { UserPlus } from '@lucide/vue'
import { request } from '../api/client'

// 인증번호 발송 API의 응답이다. 시간은 UTC ISO 문자열로 전달된다.
interface SignupEmailSendResponse {
  requestId: string
  expiresAt: string
  resendAvailableAt: string
}

// 인증 성공 후 회원가입에 사용할 일회용 확인값이다.
interface SignupEmailConfirmResponse {
  verificationToken: string
  expiresAt: string
}

const verificationUrl = '/api/auth/signup/email-verifications'

// 비밀번호 확인은 화면에서만 검사하고 서버로 전송하지 않는다.
const form = reactive({
  email: '',
  name: '',
  nickname: '',
  password: '',
  passwordConfirm: '',
  age: '' as number | '',
  gender: '' as '' | 'MALE' | 'FEMALE',
  emailNotificationEnabled: false,
})

const message = ref('')
const saving = ref(false)
const completed = ref(false)

// 이메일 인증 처리 상태
const emailInput = ref<HTMLInputElement | null>(null)
const sendingCode = ref(false)
const confirmingCode = ref(false)
const verificationMessage = ref('')
const verificationError = ref('')

// 인증번호 발급 정보
const requestId = ref('')
const requestedEmail = ref('')
const verificationCode = ref('')
const codeExpiresAt = ref(0)
const resendAvailableAt = ref(0)

// 인증 완료 정보: 서버가 반환한 확인값을 회원가입 시 함께 보낸다.
const verificationToken = ref('')
const verifiedEmail = ref('')
const proofExpiresAt = ref(0)

// 만료 시간과 재발송 대기 시간을 화면에 반영한다.
const now = ref(Date.now())
let clockTimer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (clockTimer !== undefined) {
    clearInterval(clockTimer)
  }
})

const busy = computed(() => saving.value || sendingCode.value || confirmingCode.value)

// 서버와 같은 방식으로 이메일을 정규화한다.
const normalizedEmail = computed(() => form.email.trim().toLowerCase())

// 표시용 시간이 지나면 서버 응답 없이도 화면에서 만료 상태로 바뀐다.
// 최종 유효성 검사는 서버가 다시 수행한다.
const isEmailVerified = computed(
  () =>
    verificationToken.value !== '' &&
    verifiedEmail.value === normalizedEmail.value &&
    proofExpiresAt.value > now.value,
)

const codeRemainingSeconds = computed(() =>
  Math.max(0, Math.ceil((codeExpiresAt.value - now.value) / 1000)),
)

const proofRemainingSeconds = computed(() =>
  Math.max(0, Math.ceil((proofExpiresAt.value - now.value) / 1000)),
)

const resendRemainingSeconds = computed(() =>
  Math.max(0, Math.ceil((resendAvailableAt.value - now.value) / 1000)),
)

// 인증 성공 후에는 인증번호 입력란을 감춘다.
// 가입 확인값이 만료되면 다시 발급받도록 안내한다.
const showCodeInput = computed(() => requestId.value !== '' && verificationToken.value === '')

const sendButtonText = computed(() => {
  if (sendingCode.value) return '발송 중...'
  if (isEmailVerified.value) return '인증 완료'

  if (resendRemainingSeconds.value > 0) {
    return `${resendRemainingSeconds.value}초 후 재발송`
  }

  return requestId.value ? '인증번호 재발송' : '인증번호 발송'
})

/**
 * 이메일을 변경하면 이전 인증번호와 가입 확인값을 폐기한다.
 * 인증한 이메일과 다른 이메일로 가입하지 않도록 한다.
 */
watch(
  () => form.email,
  () => {
    clearVerification()
    verificationMessage.value = ''
    verificationError.value = ''
    message.value = ''

    // 재발송 대기 시간은 유지한다.
    // 입력값 변경만으로 화면의 발송 제한이 초기화되지 않도록 한다.
  },
  { flush: 'sync' },
)

/**
 * 화면에 보관 중인 인증 정보를 초기화한다.
 * 서버의 기존 요청 무효화는 새 발급 요청에서 처리한다.
 */
function clearVerification() {
  requestId.value = ''
  requestedEmail.value = ''
  verificationCode.value = ''
  codeExpiresAt.value = 0
  verificationToken.value = ''
  verifiedEmail.value = ''
  proofExpiresAt.value = 0
}

function formatRemaining(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60

  return `${minutes}:${String(rest).padStart(2, '0')}`
}

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

/**
 * 이메일 형식을 확인하고 인증번호 발송을 요청한다.
 * 이름이나 비밀번호를 입력하기 전에도 발송할 수 있다.
 */
async function sendVerificationCode() {
  now.value = Date.now()

  if (busy.value || completed.value || isEmailVerified.value || resendRemainingSeconds.value > 0) {
    return
  }

  verificationMessage.value = ''
  verificationError.value = ''
  message.value = ''

  // 전체 가입 폼 대신 이메일 입력값만 검사한다.
  if (!emailInput.value?.reportValidity()) return

  const email = normalizedEmail.value

  clearVerification()
  sendingCode.value = true

  try {
    const result = await request<SignupEmailSendResponse>(`${verificationUrl}/send`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email }),
    })

    if (!result) {
      throw new Error('인증번호 발급 정보를 확인하지 못했습니다.')
    }

    const expiresAt = Date.parse(result.expiresAt)
    const resendAt = Date.parse(result.resendAvailableAt)

    if (!Number.isFinite(expiresAt) || !Number.isFinite(resendAt)) {
      throw new Error('인증번호 유효 시간을 확인하지 못했습니다.')
    }

    // 요청 도중 이메일이 바뀌었다면 이전 이메일의 응답은 사용하지 않는다.
    if (email !== normalizedEmail.value) return

    requestId.value = result.requestId
    requestedEmail.value = email
    codeExpiresAt.value = expiresAt
    resendAvailableAt.value = resendAt
    now.value = Date.now()

    verificationMessage.value = '인증번호를 발송했습니다. 이메일을 확인해 주세요.'
  } catch (error: unknown) {
    verificationError.value = getErrorMessage(error, '인증번호 발송 중 오류가 발생했습니다.')
  } finally {
    sendingCode.value = false
  }
}

/**
 * 발급받은 요청 ID·이메일·인증번호를 함께 검증한다.
 * 성공하면 일회용 가입 확인값을 화면 메모리에만 보관한다.
 */
async function confirmVerificationCode() {
  now.value = Date.now()

  if (busy.value || completed.value || isEmailVerified.value) return

  verificationMessage.value = ''
  verificationError.value = ''

  if (!requestId.value || requestedEmail.value !== normalizedEmail.value) {
    verificationError.value = '인증번호를 먼저 발송해 주세요.'
    return
  }

  if (codeRemainingSeconds.value <= 0) {
    verificationError.value = '인증번호가 만료됐습니다. 다시 발송해 주세요.'
    return
  }

  if (!/^[0-9]{6}$/.test(verificationCode.value)) {
    verificationError.value = '인증번호는 숫자 6자리로 입력해 주세요.'
    return
  }

  const email = requestedEmail.value
  const currentRequestId = requestId.value

  confirmingCode.value = true

  try {
    const result = await request<SignupEmailConfirmResponse>(`${verificationUrl}/confirm`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email,
        requestId: currentRequestId,
        code: verificationCode.value,
      }),
    })

    if (!result) {
      throw new Error('이메일 인증 결과를 확인하지 못했습니다.')
    }

    const expiresAt = Date.parse(result.expiresAt)

    if (!Number.isFinite(expiresAt) || !/^[A-Za-z0-9_-]{43}$/.test(result.verificationToken)) {
      throw new Error('가입 인증 정보를 확인하지 못했습니다. 다시 발송해 주세요.')
    }

    if (email !== normalizedEmail.value || currentRequestId !== requestId.value) {
      return
    }

    verificationToken.value = result.verificationToken
    verifiedEmail.value = email
    proofExpiresAt.value = expiresAt
    verificationCode.value = ''
    verificationMessage.value = ''
    now.value = Date.now()
  } catch (error: unknown) {
    verificationError.value = getErrorMessage(error, '이메일 인증 중 오류가 발생했습니다.')
  } finally {
    confirmingCode.value = false
  }
}

/**
 * 이메일 인증과 비밀번호를 검사한 뒤 회원가입을 요청한다.
 * 가입 성공 후 자동 로그인하며, 자동 로그인 실패 시 가입 완료 상태를 유지한다.
 */
async function submitSignup() {
  now.value = Date.now()

  if (busy.value || completed.value) return

  message.value = ''

  if (!isEmailVerified.value) {
    message.value = '이메일 인증을 완료해 주세요. 인증이 만료됐다면 다시 진행해 주세요.'
    return
  }

  if (form.password !== form.passwordConfirm) {
    message.value = '비밀번호가 일치하지 않습니다.'
    return
  }

  if (new TextEncoder().encode(form.password).length > 72) {
    message.value = '비밀번호가 너무 깁니다. 조금 더 짧게 입력해 주세요.'
    return
  }

  // 가입과 자동 로그인에서 같은 이메일·비밀번호를 사용한다.
  const email = normalizedEmail.value
  const password = form.password

  saving.value = true

  try {
    await request<void>('/api/auth/signup', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email,
        name: form.name.trim() || null,
        nickname: form.nickname.trim(),
        password,
        age: form.age === '' ? null : form.age,
        gender: form.gender === '' ? null : form.gender,
        emailVerificationToken: verificationToken.value,
        emailNotificationEnabled: form.emailNotificationEnabled,
      }),
    })

    completed.value = true
    clearVerification()
    verificationMessage.value = ''
    verificationError.value = ''

    try {
      await request<void>('/api/auth/login', {
        method: 'POST',
        body: new URLSearchParams({
          email,
          password,
        }),
      })

      window.location.replace('/')
    } catch {
      message.value = '회원가입은 완료됐습니다. 로그인 화면에서 로그인해 주세요.'
    } finally {
      form.password = ''
      form.passwordConfirm = ''
    }
  } catch (error: unknown) {
    message.value = getErrorMessage(error, '회원가입 중 오류가 발생했습니다.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="crud-page signup-page">
    <section class="card signup-card" aria-labelledby="signup-heading">
      <header class="page-heading">
        <div class="page-icon">
          <UserPlus :size="28" aria-hidden="true" />
        </div>
        <div>
          <h1 id="signup-heading">회원가입</h1>
          <p>MyHome에서 나만의 자취 생활을 시작하세요.</p>
        </div>
      </header>

      <form @submit.prevent="submitSignup">
        <!-- 인증번호 발송·확인·가입 처리 중에는 입력과 중복 요청을 막는다. -->
        <fieldset :disabled="busy || completed">
          <div class="form-grid">
            <div class="full-width email-verification">
              <label for="signup-email">이메일 *</label>

              <div class="verification-row">
                <input
                  id="signup-email"
                  ref="emailInput"
                  v-model.trim="form.email"
                  type="email"
                  autocomplete="username"
                  maxlength="254"
                  placeholder="example@email.com"
                  aria-describedby="signup-email-guide"
                  required
                />

                <button
                  type="button"
                  class="btn btn-secondary verification-button"
                  :disabled="
                    busy ||
                    completed ||
                    !form.email.trim() ||
                    isEmailVerified ||
                    resendRemainingSeconds > 0
                  "
                  @click="sendVerificationCode"
                >
                  {{ sendButtonText }}
                </button>
              </div>

              <p id="signup-email-guide" class="field-help">
                가입 전에 이메일 인증이 필요합니다. 이메일을 변경하면 다시 인증해야 합니다.
              </p>

              <div v-if="showCodeInput" class="verification-code">
                <label for="signup-email-code">인증번호</label>

                <div class="verification-row">
                  <input
                    id="signup-email-code"
                    v-model.trim="verificationCode"
                    type="text"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    maxlength="6"
                    placeholder="숫자 6자리"
                    :disabled="codeRemainingSeconds <= 0"
                    aria-describedby="signup-code-time"
                    @keydown.enter.prevent="confirmVerificationCode"
                  />

                  <button
                    type="button"
                    class="btn btn-primary verification-button"
                    :disabled="
                      busy || codeRemainingSeconds <= 0 || !/^[0-9]{6}$/.test(verificationCode)
                    "
                    @click="confirmVerificationCode"
                  >
                    {{ confirmingCode ? '확인 중...' : '인증 확인' }}
                  </button>
                </div>

                <p
                  id="signup-code-time"
                  class="field-help"
                  :class="{ 'verification-error': codeRemainingSeconds <= 0 }"
                >
                  {{
                    codeRemainingSeconds > 0
                      ? `인증번호 유효 시간 ${formatRemaining(codeRemainingSeconds)}`
                      : '인증번호가 만료됐습니다. 다시 발송해 주세요.'
                  }}
                </p>
              </div>

              <p v-if="isEmailVerified" class="verification-success" role="status">
                이메일 인증이 완료됐습니다.
                {{ formatRemaining(proofRemainingSeconds) }} 이내에 가입해 주세요.
              </p>

              <p
                v-else-if="verificationToken && !completed"
                class="verification-error"
                role="status"
              >
                가입 인증 유효 시간이 지났습니다. 인증번호를 다시 발송해 주세요.
              </p>

              <p v-if="verificationMessage" class="field-help" role="status">
                {{ verificationMessage }}
              </p>

              <p v-if="verificationError" class="verification-error" role="alert">
                {{ verificationError }}
              </p>
            </div>

            <label class="full-width">
              이름 (선택)
              <input
                v-model.trim="form.name"
                type="text"
                autocomplete="name"
                maxlength="100"
                placeholder="이름"
              />
            </label>

            <label class="full-width">
              닉네임
              <input
                v-model.trim="form.nickname"
                type="text"
                autocomplete="nickname"
                maxlength="100"
                placeholder="화면에 표시할 이름"
                required
              />
            </label>

            <label class="full-width">
              비밀번호
              <input
                v-model="form.password"
                type="password"
                autocomplete="new-password"
                minlength="12"
                placeholder="12자 이상 입력해 주세요"
                required
              />
            </label>

            <label class="full-width">
              비밀번호 확인
              <input
                v-model="form.passwordConfirm"
                type="password"
                autocomplete="new-password"
                placeholder="비밀번호를 다시 입력해 주세요"
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
                placeholder="나이"
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
            <div class="full-width notification-consent">
              <label class="consent-label" for="signup-email-notification">
                <input
                  id="signup-email-notification"
                  v-model="form.emailNotificationEnabled"
                  type="checkbox"
                  aria-describedby="signup-notification-guide"
                />

                <span>이메일 알림 수신에 동의합니다. (선택)</span>
              </label>

              <p id="signup-notification-guide" class="field-help">
                장보기 예정 알림을 이메일로 받습니다. 가입 후 회원 정보에서 변경할 수 있습니다.
              </p>
            </div>
          </div>

          <button
            type="submit"
            class="btn btn-primary signup-submit"
            :disabled="busy || completed || !isEmailVerified"
          >
            {{ completed ? '가입 완료' : saving ? '가입 중...' : '회원가입' }}
          </button>
        </fieldset>

        <p v-if="message" class="signup-message" role="status">
          {{ message }}
        </p>
      </form>

      <p class="login-guide">
        이미 계정이 있나요?
        <RouterLink to="/login">로그인</RouterLink>
      </p>
    </section>
  </div>
</template>

<style scoped>
.signup-page {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.signup-page .signup-card {
  width: 100%;
  max-width: 520px;
  margin: 0;
}

.full-width {
  grid-column: 1 / -1;
}

.signup-page .signup-submit {
  width: 100%;
  min-height: 48px;
  margin-top: 24px;
}

.signup-message {
  margin: 16px 0 0;
  color: #475569;
  font-size: 14px;
}

.login-guide {
  margin: 24px 0 0;
  text-align: center;
  color: #64748b;
  font-size: 14px;
}

.login-guide a {
  margin-left: 8px;
  color: #2563eb;
}

email-verification {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.verification-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
}

.verification-row input {
  flex: 1;
  width: 0;
  min-width: 0;
}

.verification-button {
  flex-shrink: 0;
  min-width: 112px;
  white-space: nowrap;
}

.verification-code {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.field-help,
.verification-success,
.verification-error {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.field-help {
  color: #64748b;
}

.verification-success {
  color: #047857;
}

.verification-error {
  color: #dc2626;
}

.notification-consent {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

/* 공통 form-grid label 스타일보다 구체적으로 지정한다. */
.signup-page .form-grid .consent-label {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
}

.consent-label input[type='checkbox'] {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  margin: 2px 0 0;
  accent-color: #2563eb;
}

.consent-label span {
  line-height: 1.6;
}

@media (max-width: 480px) {
  .verification-row {
    flex-direction: column;
  }

  .verification-row input,
  .verification-button {
    width: 100%;
  }
}
</style>
