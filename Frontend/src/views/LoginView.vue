<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { House, LogIn, Eye, EyeOff } from '@lucide/vue'
import { request } from '../api/client'

// 사용자가 입력하는 로그인 정보다.
const email = ref('')
const password = ref('')

// 비밀번호 표시 여부와 안내 메시지를 관리한다.
const showPassword = ref(false)
const message = ref('')

// 중복 로그인 요청을 방지한다.
const submitting = ref(false)

/**
 * CSRF 토큰을 조회한 뒤 이메일·비밀번호를 폼 형식으로 전송한다.
 * 로그인 실패는 화면에 안내하고, 성공하면 대시보드를 새로 연다.
 * 직접 fetch를 사용하므로 CSRF 토큰도 이 함수에서 전달한다.
 */
async function submitLogin() {
  if (submitting.value) return

  message.value = ''
  submitting.value = true

  try {
    const csrf = await request<{
      headerName: string
      token: string
    }>('/api/auth/csrf')

    if (!csrf) {
      throw new Error('요청을 준비하지 못했습니다. 다시 시도해 주세요.')
    }

    // 로그인 실패의 401은 일반적인 비로그인 접근과 구분해 안내한다.
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

    if (response.status === 401) {
      throw new Error('이메일 또는 비밀번호를 확인해 주세요.')
    }

    if (response.status === 403) {
      throw new Error('요청이 만료됐습니다. 다시 로그인해 주세요.')
    }

    if (!response.ok) {
      throw new Error('로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }

    // 앱을 다시 열어 회원 카드와 대시보드에 로그인 상태를 반영한다.
    window.location.replace('/')
  } catch (error) {
    message.value = error instanceof Error ? error.message : '서버에 연결하지 못했습니다.'
  } finally {
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
              type="email"
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

      <div class="login-links">
        <RouterLink to="/signup">회원가입</RouterLink>
        <span aria-hidden="true">|</span>
        <button type="button" disabled>비밀번호 찾기 · 준비 중</button>
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

.login-links button {
  padding: 0;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: not-allowed;
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
