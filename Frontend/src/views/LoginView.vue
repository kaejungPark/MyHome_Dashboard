<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { House, LogIn, Eye, EyeOff } from '@lucide/vue'
import { request } from '../api/client'
import type { ApiResponse } from '../types/api'

/**
 * 로그인 성공 응답의 data에 들어오는 정보다.
 *
 * false: 일반 로그인으로 대시보드 이동
 * true: 임시 로그인으로 비밀번호 변경 화면 이동
 *
 * 값은 프론트에서 판단하지 않고 서버의 인증 결과를 사용한다.
 */
interface LoginResult {
  passwordChangeRequired: boolean
}

// 사용자가 입력하는 로그인 정보다.
// 비밀번호에는 일반 비밀번호와 이메일로 받은 임시 비밀번호 모두 입력할 수 있다.
const email = ref('')
const password = ref('')

// 비밀번호 표시 여부와 로그인 오류 안내를 관리한다.
const showPassword = ref(false)
const message = ref('')

// 로그인 버튼을 연속으로 눌러 중복 요청하는 것을 방지한다.
const submitting = ref(false)

/**
 * 이메일·비밀번호를 폼 형식으로 전송하여 로그인한다.
 *
 * 처리 순서:
 * 1. 현재 세션의 CSRF 토큰 조회
 * 2. 기존 로그인 API로 폼 데이터 전송
 * 3. HTTP 상태와 응답 데이터 확인
 * 4. 서버가 반환한 변경 필요 여부에 따라 화면 이동
 *
 * 로그인은 직접 fetch를 사용하므로 CSRF 헤더를 직접 추가하고,
 * ApiResponse의 success·data도 이 함수에서 확인한다.
 */
async function submitLogin() {
  if (submitting.value) return

  message.value = ''
  submitting.value = true

  try {
    // 로그인 POST 요청에 사용할 현재 세션의 CSRF 토큰을 조회한다.
    const csrf = await request<{
      headerName: string
      token: string
    }>('/api/auth/csrf')

    if (!csrf) {
      throw new Error('요청을 준비하지 못했습니다. 다시 시도해 주세요.')
    }

    /*
     * Spring Security의 formLogin은 JSON이 아닌 폼 데이터를 읽는다.
     * 기존 usernameParameter("email"), passwordParameter("password")와
     * 동일한 이름으로 전송해야 한다.
     *
     * 비밀번호는 공백도 값의 일부이므로 trim하지 않는다.
     */
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        [csrf.headerName]: csrf.token,
      },
      body: new URLSearchParams({
        email: email.value.trim(),
        password: password.value,
      }),
    })

    // 일반·임시 비밀번호 검증 실패는 동일한 안내로 처리한다.
    // 계정 존재 여부나 임시 비밀번호 상태를 별도로 노출하지 않는다.
    if (response.status === 401) {
      throw new Error('이메일 또는 비밀번호를 확인해 주세요.')
    }

    // 로그인 요청의 CSRF 토큰이 유효하지 않은 경우 등을 안내한다.
    if (response.status === 403) {
      throw new Error('요청이 거절됐습니다. 화면을 새로고침한 후 다시 로그인해 주세요.')
    }

    // 서버 오류는 비밀번호 불일치와 구분한다.
    if (!response.ok) {
      throw new Error('로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }

    /*
     * 직접 fetch를 사용했으므로 응답 전체를 읽는다.
     *
     * 응답 예:
     * {
     *   success: true,
     *   data: { passwordChangeRequired: true },
     *   message: null
     * }
     */
    let result: ApiResponse<LoginResult> | null = null

    try {
      result = (await response.json()) as ApiResponse<LoginResult>
    } catch {
      throw new Error('로그인 응답을 확인하지 못했습니다. 다시 시도해 주세요.')
    }

    // HTTP 요청이 성공했어도 API 처리 결과가 실패이면 이동하지 않는다.
    if (!result?.success) {
      throw new Error(result?.message || '로그인 결과를 확인하지 못했습니다.')
    }

    /*
     * TypeScript 타입 선언만으로 실제 서버 응답이 검증되지는 않는다.
     * 변경 필요 여부가 없거나 boolean이 아니면 이동을 중단한다.
     *
     * 누락된 값을 false로 처리하면 임시 로그인 사용자를
     * 대시보드로 잘못 이동시킬 수 있다.
     */
    if (!result.data || typeof result.data.passwordChangeRequired !== 'boolean') {
      throw new Error('로그인 상태 정보를 확인하지 못했습니다. 다시 시도해 주세요.')
    }

    // 로그인에 성공한 비밀번호 원문은 화면 상태에서 비운다.
    // URL·localStorage·sessionStorage 등에 저장하지 않는다.
    password.value = ''

    /*
     * 앱을 다시 열어 App.vue도 새 세션의 로그인 정보를 조회하게 한다.
     * replace를 사용하여 뒤로 가기로 로그인 제출 화면에 돌아가지 않게 한다.
     *
     * 화면 이동은 사용자 안내용이며,
     * 실제 API 접근 제한은 백엔드 PasswordStateFilter가 담당한다.
     */
    const targetPath = result.data.passwordChangeRequired ? '/change-password' : '/'

    window.location.replace(targetPath)
  } catch (error: unknown) {
    // 오류 발생 시 현재 로그인 화면에서 안내한다.
    message.value = error instanceof Error ? error.message : '서버에 연결하지 못했습니다.'
  } finally {
    // 실패했을 때 다시 로그인할 수 있도록 요청 상태를 해제한다.
    submitting.value = false
  }
}
</script>
<template>
  <div class="crud-page login-page">
    <section class="card login-card" aria-labelledby="login-heading">
      <header class="login-heading">
        <div class="login-symbol">
          <House :size="32" aria-hidden="true" />
        </div>
        <h1 id="login-heading">MyHome 로그인</h1>
        <p>로그인하고 나만의 자취 생활을 관리하세요.</p>
      </header>

      <form @submit.prevent="submitLogin">
        <div class="form-grid login-fields">
          <label for="login-email">
            이메일
            <input
              id="login-email"
              v-model="email"
              name="email"
              :type="email.trim() === 'admin' ? 'text' : 'email'"
              autocomplete="username"
              placeholder="이메일을 입력해 주세요"
              maxlength="254"
              required
            />
          </label>

          <div class="password-field">
            <label for="login-password">
              비밀번호
              <input
                id="login-password"
                v-model="password"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="비밀번호를 입력해 주세요"
                :disabled="submitting"
                required
              />
            </label>

            <button
              type="button"
              class="password-toggle"
              :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'"
              :aria-pressed="showPassword"
              aria-controls="login-password"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="20" aria-hidden="true" />
              <Eye v-else :size="20" aria-hidden="true" />
            </button>
          </div>
        </div>

        <p v-if="message" class="login-message" role="status">
          {{ message }}
        </p>

        <button type="submit" class="btn btn-primary login-submit" :disabled="submitting">
          <LogIn :size="18" aria-hidden="true" />
          {{ submitting ? '로그인 중...' : '로그인' }}
        </button>
      </form>

      <!-- 회원가입과 비밀번호 찾기 화면으로 이동하는 링크다. -->
      <div class="login-links">
        <RouterLink to="/signup">회원가입</RouterLink>

        <span aria-hidden="true">|</span>

        <RouterLink to="/forgot-password">비밀번호 찾기</RouterLink>
      </div>

      <RouterLink to="/" class="back-link"> 대시보드로 돌아가기 </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.login-page .login-card {
  width: 100%;
  max-width: 440px;
  margin: 0;
  padding: 36px;
  border-radius: 20px;
  box-shadow: 0 8px 28px rgb(30 41 59 / 4%);
}

.login-heading {
  margin-bottom: 32px;
  text-align: center;
}

.login-symbol {
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

.login-heading h1 {
  margin: 0 0 8px;
  font-size: 26px;
}

.login-heading p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  word-break: keep-all;
}

.login-fields {
  grid-template-columns: minmax(0, 1fr);
}

.password-field {
  position: relative;
}

.login-fields .password-field input {
  padding-right: 48px;
}

.password-toggle {
  position: absolute;
  right: 4px;
  bottom: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.password-toggle:hover {
  background: #f1f5f9;
}

.login-message {
  margin: 16px 0 0;
  color: #64748b;
  font-size: 14px;
}

.login-page .login-submit {
  width: 100%;
  min-height: 48px;
  margin-top: 24px;
  font-size: 15px;
}

.login-links {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
  color: #cbd5e1;
}

/* 회원가입·비밀번호 찾기 링크의 표시를 통일한다. */
.login-links a {
  color: #2563eb;
  font-size: 13px;
  text-decoration: none;
}

.login-links a:hover {
  text-decoration: underline;
}

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

@media (max-width: 480px) {
  .login-page {
    padding: 16px 0;
  }

  .login-page .login-card {
    padding: 28px 20px;
  }

  .login-heading h1 {
    font-size: 24px;
  }
}
</style>
