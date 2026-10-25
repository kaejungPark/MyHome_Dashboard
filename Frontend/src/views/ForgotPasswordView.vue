<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { KeyRound, Mail } from '@lucide/vue'
import { request } from '../api/client'

// 임시 비밀번호 발송을 요청할 회원의 이메일이다.
const email = ref('')

// 발송 요청 중 버튼을 여러 번 누르는 것을 방지한다.
const sending = ref(false)

// 서버에서 반환한 일반 안내 문구와 요청 오류를 구분하여 표시한다.
const noticeMessage = ref('')
const errorMessage = ref('')

// 재요청까지 남은 시간이다.
// 화면의 타이머는 안내용이며 실제 발급 제한은 백엔드에서 검사한다.
const remainingSeconds = ref(0)

// 화면을 벗어날 때 정리할 재요청 대기 타이머다.
let resendTimer: ReturnType<typeof setInterval> | undefined

// 화면을 벗어난 뒤 응답이 도착하여 타이머가 생성되지 않도록
// 진행 중인 발송 요청을 취소하는 데 사용한다.
let sendController: AbortController | undefined

/**
 * 요청 중이거나 재요청 대기 시간이 남아 있으면 버튼을 비활성화한다.
 * 이메일 형식은 HTML 입력 검증과 백엔드 검증을 함께 사용한다.
 */
const sendDisabled = computed(() => sending.value || remainingSeconds.value > 0)

/**
 * 현재 요청 상태에 따라 발송 버튼의 문구를 표시한다.
 * 서버의 성공 응답만으로 실제 메일 도착을 확정하지 않는다.
 */
const sendButtonLabel = computed(() => {
  if (sending.value) {
    return '요청 중...'
  }

  if (remainingSeconds.value > 0) {
    return `${remainingSeconds.value}초 후 다시 요청`
  }

  return '임시 비밀번호 발송'
})

/**
 * 기존 타이머를 종료한다.
 * 재요청 시 타이머가 중복 생성되거나 화면 종료 후 남는 것을 방지한다.
 */
function clearResendTimer() {
  if (resendTimer !== undefined) {
    clearInterval(resendTimer)
    resendTimer = undefined
  }
}

/**
 * 정상 응답을 받은 뒤 60초 동안 재요청 버튼을 비활성화한다.
 *
 * 매번 1씩 차감하지 않고 종료 시각과 현재 시각의 차이를 계산한다.
 * 브라우저가 백그라운드에서 타이머 실행을 늦춰도
 * 다음 실행 시 실제 경과 시간을 반영할 수 있다.
 *
 * 새로고침하면 화면 타이머는 초기화되지만
 * 서버의 재발급 제한은 계속 유지된다.
 */
function startResendCooldown() {
  clearResendTimer()

  const availableAt = Date.now() + 60_000
  remainingSeconds.value = 60

  resendTimer = setInterval(() => {
    remainingSeconds.value = Math.max(0, Math.ceil((availableAt - Date.now()) / 1000))

    if (remainingSeconds.value === 0) {
      clearResendTimer()
    }
  }, 1000)
}

/**
 * 이메일 입력을 수정하면 이전 입력에 대한 안내를 지운다.
 * 입력 변경으로 재요청 대기 시간을 초기화하지는 않는다.
 */
function clearMessages() {
  noticeMessage.value = ''
  errorMessage.value = ''
}

/**
 * 가입한 이메일로 임시 비밀번호 발송을 요청한다.
 *
 * 처리 순서:
 * 1. 중복 요청과 재요청 대기 여부 검사
 * 2. 이메일 공백 제거 및 소문자 변환
 * 3. 공통 request()로 POST 요청
 * 4. 서버 안내 표시 및 재요청 대기 시작
 *
 * request()가 CSRF 토큰을 자동으로 조회하여 헤더에 추가한다.
 * 반환값은 ApiResponse 전체가 아닌 data에 담긴 문자열이다.
 */
async function sendTemporaryPassword() {
  if (sendDisabled.value) return

  clearMessages()

  // 회원가입·백엔드와 동일하게 이메일을 정규화한다.
  const normalizedEmail = email.value.trim().toLowerCase()

  if (!normalizedEmail) {
    errorMessage.value = '이메일을 입력해 주세요.'
    return
  }

  email.value = normalizedEmail
  sending.value = true

  const controller = new AbortController()
  sendController = controller

  try {
    const message = await request<string>('/api/auth/password-reset', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: normalizedEmail,
      }),
      signal: controller.signal,
    })

    // 화면을 벗어나 취소된 요청이라면 안내나 타이머를 생성하지 않는다.
    if (controller.signal.aborted) return

    if (!message) {
      throw new Error('서버 응답을 확인하지 못했습니다. 다시 시도해 주세요.')
    }

    /*
     * 회원 존재 여부·발급 제한·메일 발송 실패를 구분하지 않는
     * 백엔드의 일반 안내 문구를 그대로 표시한다.
     *
     * "메일 발송 완료"라고 단정하면 실제 처리 결과와 다를 수 있다.
     */
    noticeMessage.value = message

    startResendCooldown()
  } catch (error: unknown) {
    // 화면 이동으로 직접 취소한 요청은 오류로 표시하지 않는다.
    if (controller.signal.aborted) return

    errorMessage.value =
      error instanceof Error
        ? error.message
        : '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    sending.value = false

    if (sendController === controller) {
      sendController = undefined
    }
  }
}

/**
 * 화면이 제거되면 타이머와 진행 중인 요청을 정리한다.
 * 요청 취소가 이미 서버에서 시작한 메일 발송까지 취소하는 것은 아니다.
 */
onUnmounted(() => {
  clearResendTimer()
  sendController?.abort()
})
</script>

<template>
  <div class="crud-page forgot-password-page">
    <section class="card forgot-password-card" aria-labelledby="forgot-password-heading">
      <!-- 기존 로그인 화면과 동일한 중앙 카드 형태의 제목 영역이다. -->
      <header class="forgot-password-heading">
        <div class="forgot-password-symbol">
          <KeyRound :size="30" aria-hidden="true" />
        </div>

        <h1 id="forgot-password-heading">비밀번호 찾기</h1>
        <p>가입한 이메일로 임시 비밀번호를 요청하세요.</p>
      </header>

      <!--
        브라우저의 required·email 형식 검증을 통과하면 발송 함수를 실행한다.
        제출 시 페이지가 새로고침되지 않도록 prevent를 사용한다.
      -->
      <form :aria-busy="sending" @submit.prevent="sendTemporaryPassword">
        <div class="form-grid forgot-password-fields">
          <label for="password-reset-email">
            이메일

            <input
              id="password-reset-email"
              v-model.trim="email"
              name="email"
              type="email"
              autocomplete="email"
              autocapitalize="none"
              :spellcheck="false"
              placeholder="가입한 이메일을 입력해 주세요"
              maxlength="254"
              required
              :disabled="sending"
              aria-describedby="password-reset-email-help"
              @input="clearMessages"
            />
          </label>
        </div>

        <p id="password-reset-email-help" class="field-help">
          회원가입 시 인증한 이메일을 입력해 주세요.
        </p>

        <!-- 서버의 일반 안내와 통신·입력 오류를 구분하여 표시한다. -->
        <p v-if="noticeMessage" class="request-message request-message-notice" role="status">
          {{ noticeMessage }}
        </p>

        <p v-if="errorMessage" class="request-message request-message-error" role="alert">
          {{ errorMessage }}
        </p>

        <button type="submit" class="btn btn-primary send-button" :disabled="sendDisabled">
          <Mail :size="18" aria-hidden="true" />
          {{ sendButtonLabel }}
        </button>
      </form>

      <!-- 임시 비밀번호를 받은 후 진행할 절차를 안내한다. -->
      <div class="password-reset-guide">
        <p class="guide-title">메일을 받은 후 이렇게 진행해 주세요.</p>

        <ol>
          <li>로그인 화면에서 이메일과 임시 비밀번호를 입력해 주세요.</li>
          <li>로그인 후 사용할 새 비밀번호로 변경해 주세요.</li>
        </ol>

        <p class="guide-note">
          임시 비밀번호는 15분 동안 유효합니다. 새 비밀번호로 변경하기 전까지 기존 비밀번호는
          유지됩니다.
        </p>
      </div>

      <RouterLink to="/login" class="back-link"> 로그인 화면으로 돌아가기 </RouterLink>
    </section>
  </div>
</template>

<style scoped>
/* 로그인 화면처럼 카드를 중앙에 배치한다. */
.forgot-password-page {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

/* 공통 card 스타일을 유지하면서 인증 화면의 너비와 여백을 지정한다. */
.forgot-password-page .forgot-password-card {
  width: 100%;
  max-width: 440px;
  min-width: 0;
  margin: 0;
  padding: 36px;
  border-radius: 20px;
  box-shadow: 0 8px 28px rgb(30 41 59 / 4%);
}

/* 화면 제목과 아이콘 */
.forgot-password-heading {
  margin-bottom: 28px;
  text-align: center;
}

.forgot-password-symbol {
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

.forgot-password-heading h1 {
  margin: 0 0 8px;
  font-size: 26px;
}

.forgot-password-heading > p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
  word-break: keep-all;
}

/* 입력란은 기존 공통 form-grid 스타일을 사용하고 한 열로 배치한다. */
.forgot-password-fields {
  grid-template-columns: minmax(0, 1fr);
}

.field-help {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

/* 정상 안내와 오류의 색상을 구분한다. */
.request-message {
  margin: 16px 0 0;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.request-message-notice {
  background: #eff6ff;
  color: #1d4ed8;
}

.request-message-error {
  background: #fef2f2;
  color: #dc2626;
}

/* 발송 버튼은 카드의 입력 영역 너비를 모두 사용한다. */
.forgot-password-page .send-button {
  width: 100%;
  min-height: 48px;
  margin-top: 24px;
  font-size: 15px;
}

/* 임시 비밀번호 사용 절차 */
.password-reset-guide {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.guide-title {
  margin: 0;
  color: #334155;
  font-weight: 600;
}

.password-reset-guide ol {
  margin: 8px 0 0;
  padding-left: 20px;
}

.password-reset-guide li + li {
  margin-top: 4px;
}

.guide-note {
  margin: 12px 0 0;
  font-size: 12px;
}

/* 로그인 화면으로 돌아가는 링크 */
.back-link {
  display: block;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #eef2f7;
  color: #64748b;
  font-size: 13px;
  text-align: center;
  text-decoration: none;
}

.back-link:hover {
  color: #2563eb;
}

/* 작은 화면에서는 카드 여백을 줄여 입력 공간을 확보한다. */
@media (max-width: 480px) {
  .forgot-password-page {
    padding: 16px 0;
  }

  .forgot-password-page .forgot-password-card {
    padding: 28px 20px;
  }

  .forgot-password-heading h1 {
    font-size: 24px;
  }
}
</style>
