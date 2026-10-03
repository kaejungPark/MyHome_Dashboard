<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { UserPlus } from '@lucide/vue'
import { request } from '../api/client'

// 이름·나이·성별은 선택 입력이다.
// 비밀번호 확인은 화면 검증에만 사용하고 서버로 전송하지 않는다.
const form = reactive({
  email: '',
  name: '',
  nickname: '',
  password: '',
  passwordConfirm: '',
  age: '' as number | '',
  gender: '' as '' | 'MALE' | 'FEMALE',
})

const message = ref('')
const saving = ref(false) // 중복 제출을 방지하고 가입 완료 상태를 관리한다.
const completed = ref(false) // 가입 성공 후 자동 로그인에 실패하더라도 중복 가입을 막는다.

/**
 * 비밀번호 일치 여부와 길이를 확인한 뒤 회원가입을 요청한다.
 * 가입 성공 후 별도 로그인 요청으로 세션을 생성하고 대시보드로 이동한다.
 * 자동 로그인에 실패해도 회원가입 자체는 완료된 상태로 유지한다.
 */
async function submitSignup() {
  if (saving.value || completed.value) return

  message.value = ''

  if (form.password !== form.passwordConfirm) {
    message.value = '비밀번호가 일치하지 않습니다.'
    return
  }

  if (new TextEncoder().encode(form.password).length > 72) {
    message.value = '비밀번호가 너무 깁니다. 조금 더 짧게 입력해 주세요.'
    return
  }

  saving.value = true

  try {
    await request<void>('/api/auth/signup', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: form.email,
        name: form.name.trim() || null,
        nickname: form.nickname,
        password: form.password,
        age: form.age === '' ? null : form.age,
        gender: form.gender === '' ? null : form.gender,
      }),
    })

    // 자동 로그인에 실패하더라도 다시 가입하지 않도록 한다.
    completed.value = true

    try {
      // 로그인 API는 폼 형식으로 전송한다.
      await request<void>('/api/auth/login', {
        method: 'POST',
        body: new URLSearchParams({
          email: form.email,
          password: form.password,
        }),
      })

      window.location.replace('/')
    } catch {
      message.value = '회원가입은 완료됐습니다. 로그인 화면에서 로그인해 주세요.'
    } finally {
      form.password = ''
      form.passwordConfirm = ''
    }
  } catch (error) {
    message.value = error instanceof Error ? error.message : '회원가입 중 오류가 발생했습니다.'
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
        <fieldset :disabled="saving || completed">
          <div class="form-grid">
            <label class="full-width">
              이메일
              <input
                v-model.trim="form.email"
                type="email"
                autocomplete="username"
                maxlength="254"
                placeholder="example@email.com"
                required
              />
            </label>

            <label class="full-width">
              이름
              <input
                v-model.trim="form.name"
                type="text"
                autocomplete="name"
                maxlength="100"
                placeholder="이름"
                required
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
          </div>

          <button type="submit" class="btn btn-primary signup-submit">
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
</style>
