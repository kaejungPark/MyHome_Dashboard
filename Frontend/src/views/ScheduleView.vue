<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { request } from '../api/client'

// 일정 목록 조회와 수정 폼에서 사용하는 데이터다.
interface Schedule {
  id: number
  title: string
  scheduleType: 'GENERAL' | 'PAYMENT' | 'RENEWAL'
  startDate: string
  dueDate: string
  amount: number | null
  completed: boolean
  memo: string | null
}

// 조회 결과와 화면 처리 상태를 관리한다.
const schedules = ref<Schedule[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const editingId = ref<number | null>(null)

// 사용자 PC 기준 오늘 날짜를 YYYY-MM-DD 문자열로 반환한다.
function today(): string {
  const date = new Date()

  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

// 등록 폼의 초기값이다. 금액은 빈칸으로 두고 저장 시 숫자 또는 null로 변환한다.
function initialForm() {
  return {
    title: '',
    scheduleType: 'GENERAL' as 'GENERAL' | 'PAYMENT' | 'RENEWAL',
    startDate: today(),
    dueDate: today(),
    amount: '',
    completed: false,
    memo: '',
  }
}

// 서버의 일정 유형 코드를 화면에 표시할 한글 명칭으로 변환한다.
function scheduleTypeLabel(type: string): string {
  switch (type) {
    case 'GENERAL':
      return '일반 일정'
    case 'PAYMENT':
      return '납부'
    case 'RENEWAL':
      return '갱신'
    default:
      return type
  }
}

// 폼 입력값을 화면과 동기화한다.
const form = reactive(initialForm())

// 오류 원인은 개발자 도구에 기록하고, 화면에는 안내 메시지를 표시한다.
function showError(error: unknown) {
  console.error(error)
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

async function loadData() {
  loading.value = true
  errorMessage.value = ''

  try {
    const [scheduleData] = await Promise.all([request<Schedule[]>('/api/schedules')])

    schedules.value = scheduleData ?? []
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 수정 중이면 PUT, 새 등록이면 POST로 저장한다.
async function saveSchedule() {
  if (saving.value) return

  errorMessage.value = ''
  notice.value = ''

  // 금액은 선택 입력이므로 빈칸이면 null로 처리한다.
  const amountText = String(form.amount).trim()
  const amount = amountText === '' ? null : Number(amountText)

  // 제목·유형·날짜를 필수 확인하고, 입력한 금액은 0 이상의 정수만 허용한다.
  if (
    !form.title.trim() ||
    !['GENERAL', 'PAYMENT', 'RENEWAL'].includes(form.scheduleType) ||
    !form.startDate ||
    !form.dueDate ||
    (amount !== null && (!Number.isSafeInteger(amount) || amount < 0))
  ) {
    errorMessage.value = '제목, 일정 유형, 날짜와 0 이상의 정수 금액을 확인해 주세요.'
    return
  }

  // 날짜 입력값은 YYYY-MM-DD 형식이므로 문자열로 순서를 비교한다.
  if (form.dueDate < form.startDate) {
    errorMessage.value = '마감일은 시작일보다 빠를 수 없습니다.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    // 신규 등록은 POST, 수정은 ID를 포함한 PUT 요청으로 전송한다.
    await request<null>(id === null ? '/api/schedules' : `/api/schedules/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: form.title.trim(), // 일정 제목
        scheduleType: form.scheduleType, // 일정 유형
        startDate: form.startDate, // 시작일
        dueDate: form.dueDate, // 마감일
        completed: form.completed, // 완료 여부: boolean
        amount, // 관련 금액: 미입력 시 null
        memo: form.memo.trim() || null, // 메모: 미입력 시 null
      }),
    })

    // 저장 후 폼을 초기화하고 최신 목록을 조회한다.
    resetForm()
    notice.value = id === null ? '등록됐습니다.' : '수정됐습니다.'
    await loadData()
  } catch (error: unknown) {
    // 저장 실패 시 입력 내용을 유지한다.
    showError(error)
  } finally {
    saving.value = false
  }
}

// 수정 상태를 해제하고 입력값을 신규 등록 상태로 되돌린다.
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

// 선택한 일정의 값을 수정 폼에 채운다.
function startEdit(schedule: Schedule) {
  editingId.value = schedule.id
  errorMessage.value = ''
  notice.value = ''

  Object.assign(form, {
    title: schedule.title, // 일정 제목
    scheduleType: schedule.scheduleType, // 일정 유형
    startDate: schedule.startDate, // 시작일
    dueDate: schedule.dueDate, // 마감일
    completed: schedule.completed, // 완료 여부
    amount: schedule.amount === null ? '' : String(schedule.amount), // 금액
    memo: schedule.memo ?? '', // 메모가 없으면 빈칸으로 표시
  })
}

// 일정 삭제. 처리 중 중복 요청을 막는다.
async function deleteSchedule(schedule: Schedule) {
  if (saving.value || loading.value) return
  if (!window.confirm(`"${schedule.title}" 일정을 삭제하시겠습니까?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`/api/schedules/${schedule.id}`, {
      method: 'DELETE',
    })

    if (editingId.value === schedule.id) {
      resetForm()
    }

    notice.value = '삭제됐습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
<template>
  <section>
    <h1>일정 관리</h1>
    <p>일정을 등록하고 마감일과 완료 여부를 확인합니다.</p>

    <p v-if="errorMessage" class="error" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" role="status">{{ notice }}</p>

    <form class="panel" @submit.prevent="saveSchedule">
      <h2>{{ editingId === null ? '일정 등록' : '일정 수정' }}</h2>

      <fieldset :disabled="saving || loading">
        <div class="form-grid">
          <label>
            제목
            <input v-model="form.title" required maxlength="200" />
          </label>

          <label>
            일정 유형
            <select v-model="form.scheduleType" required>
              <option value="GENERAL">일반 일정</option>
              <option value="PAYMENT">납부</option>
              <option value="RENEWAL">갱신</option>
            </select>
          </label>

          <label>
            시작일
            <input v-model="form.startDate" type="date" required />
          </label>

          <label>
            마감일
            <input v-model="form.dueDate" type="date" :min="form.startDate" required />
          </label>

          <label>
            관련 금액 · 선택
            <input
              v-model="form.amount"
              type="text"
              inputmode="numeric"
              pattern="[0-9]+"
              placeholder="금액이 없으면 비워 주세요"
            />
          </label>

          <label>
            완료 여부
            <select v-model="form.completed">
              <option :value="false">미완료</option>
              <option :value="true">완료</option>
            </select>
          </label>
        </div>

        <label class="memo-field">
          메모
          <textarea v-model="form.memo" maxlength="2000" rows="3"></textarea>
        </label>

        <div class="button-group">
          <button type="submit">
            {{ saving ? '저장 중…' : editingId === null ? '등록' : '수정 저장' }}
          </button>
          <button v-if="editingId !== null" type="button" @click="resetForm">수정 취소</button>
        </div>
      </fieldset>
    </form>

    <section class="panel">
      <div class="list-heading">
        <h2>일정 목록</h2>
        <button type="button" :disabled="loading || saving" @click="loadData">새로고침</button>
      </div>

      <p v-if="loading" role="status">조회 중입니다.</p>
      <p v-else-if="errorMessage">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="schedules.length === 0">등록된 일정이 없습니다.</p>

      <div v-if="!loading && schedules.length > 0" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">제목</th>
              <th scope="col">유형</th>
              <th scope="col">시작일</th>
              <th scope="col">마감일</th>
              <th scope="col">금액</th>
              <th scope="col">상태</th>
              <th scope="col">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="schedule in schedules" :key="schedule.id">
              <td>{{ schedule.title }}</td>
              <td>{{ scheduleTypeLabel(schedule.scheduleType) }}</td>
              <td>{{ schedule.startDate }}</td>
              <td>{{ schedule.dueDate }}</td>
              <td class="amount">
                {{
                  schedule.amount === null ? '-' : `${schedule.amount.toLocaleString('ko-KR')}원`
                }}
              </td>
              <td>{{ schedule.completed ? '완료' : '미완료' }}</td>
              <td>
                <div class="button-group">
                  <button type="button" :disabled="saving || loading" @click="startEdit(schedule)">
                    수정
                  </button>
                  <button
                    type="button"
                    :disabled="saving || loading"
                    @click="deleteSchedule(schedule)"
                  >
                    삭제
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<style scoped>
fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

input,
select,
textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font: inherit;
}

textarea {
  resize: vertical;
}

.memo-field {
  margin: 16px 0;
}

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.button-group button {
  white-space: nowrap;
}

.error {
  color: #b91c1c;
}

.list-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.list-heading h2 {
  margin: 0;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
}

.amount {
  text-align: right;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
