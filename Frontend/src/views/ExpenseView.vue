<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

interface ApiResponse<T> {
  success: boolean
  data: T | null
  message: string | null
}

interface Category {
  id: number
  name: string
}

interface Expense {
  id: number
  categoryId: number // 수정 폼에서 기존 카테고리를 선택하는 데 사용
  categoryName: string
  amount: number
  expenseDate: string
  expenseType: string
  paymentMethod: string | null
  title: string
  memo: string | null
}

const categories = ref<Category[]>([])
const expenses = ref<Expense[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const editingId = ref<number | null>(null)

// UTC 변환으로 날짜가 달라지지 않도록 로컬 날짜를 사용한다.
function today() {
  const date = new Date()
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function initialForm() {
  return {
    categoryId: '',
    amount: '',
    expenseDate: today(),
    expenseType: 'VARIABLE',
    paymentMethod: '',
    title: '',
    memo: '',
  }
}

const form = reactive(initialForm())

// HTTP 상태와 서버의 공통 응답을 함께 확인한다.
async function request<T>(url: string, options?: RequestInit): Promise<T | null> {
  const response = await fetch(url, options)
  const result: ApiResponse<T> = await response.json()

  if (!response.ok || !result.success) {
    throw new Error(result.message ?? `요청 실패 (${response.status})`)
  }

  return result.data
}

function showError(error: unknown) {
  console.error(error)
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 카테고리와 지출을 모두 받은 뒤 화면 데이터를 갱신한다.
async function loadData() {
  loading.value = true
  errorMessage.value = ''

  try {
    const [categoryData, expenseData] = await Promise.all([
      request<Category[]>('/api/categories'),
      request<Expense[]>('/api/expenses'),
    ])

    categories.value = categoryData ?? []
    expenses.value = expenseData ?? []
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 수정 중이면 PUT, 새 등록이면 POST로 저장한다.
async function saveExpense() {
  if (saving.value) return

  errorMessage.value = ''
  notice.value = ''

  const amount = Number(form.amount)
  if (
    !form.categoryId ||
    !form.title.trim() ||
    !form.expenseDate ||
    !Number.isFinite(amount) ||
    amount <= 0
  ) {
    errorMessage.value = '카테고리, 제목, 날짜와 0보다 큰 금액을 입력해 주세요.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    await request<null>(id === null ? '/api/expenses' : `/api/expenses/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        categoryId: Number(form.categoryId),
        amount,
        expenseDate: form.expenseDate,
        expenseType: form.expenseType,
        paymentMethod: form.paymentMethod.trim() || null,
        title: form.title.trim(),
        memo: form.memo.trim() || null,
      }),
    })

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

function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

// 선택한 지출을 수정 폼에 채운다.
function startEdit(expense: Expense) {
  editingId.value = expense.id
  errorMessage.value = ''
  notice.value = ''

  Object.assign(form, {
    categoryId: String(expense.categoryId),
    amount: String(expense.amount),
    expenseDate: expense.expenseDate,
    expenseType: expense.expenseType,
    paymentMethod: expense.paymentMethod ?? '',
    title: expense.title,
    memo: expense.memo ?? '',
  })

  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 확인을 받은 뒤 삭제한다. 처리 중 중복 요청을 막는다.
async function deleteExpense(expense: Expense) {
  if (saving.value || loading.value) return
  if (!window.confirm(`"${expense.title}" 지출을 삭제하시겠습니까?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`/api/expenses/${expense.id}`, {
      method: 'DELETE',
    })

    if (editingId.value === expense.id) {
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
    <h1>생활비</h1>
    <p>지출을 등록하고 사용 내역을 확인합니다.</p>

    <p v-if="errorMessage" class="error" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" role="status">{{ notice }}</p>

    <form class="panel" @submit.prevent="saveExpense">
      <h2>{{ editingId === null ? '지출 등록' : '지출 수정' }}</h2>

      <fieldset :disabled="saving || loading">
        <div class="form-grid">
          <label>
            제목
            <input v-model="form.title" required maxlength="200" />
          </label>

          <label>
            카테고리
            <select v-model="form.categoryId" required>
              <option value="" disabled>선택해 주세요</option>
              <option
                v-for="category in categories"
                :key="category.id"
                :value="String(category.id)"
              >
                {{ category.name }}
              </option>
            </select>
          </label>

          <label>
            금액
            <input v-model="form.amount" type="number" min="0.01" step="0.01" required />
          </label>

          <label>
            지출일
            <input v-model="form.expenseDate" type="date" required />
          </label>

          <label>
            지출 유형
            <select v-model="form.expenseType">
              <option value="VARIABLE">변동 지출</option>
              <option value="FIXED">고정 지출</option>
            </select>
          </label>

          <label>
            결제 수단
            <input v-model="form.paymentMethod" maxlength="50" placeholder="카드, 현금 등" />
          </label>
        </div>

        <label class="memo-field">
          메모
          <textarea v-model="form.memo" maxlength="2000" rows="3" />
        </label>

        <div class="button-group">
          <button type="submit" :disabled="categories.length === 0">
            {{ saving ? '저장 중…' : editingId === null ? '등록' : '수정 저장' }}
          </button>

          <button v-if="editingId !== null" type="button" @click="resetForm">수정 취소</button>
        </div>
      </fieldset>
    </form>

    <section class="panel">
      <div class="list-heading">
        <h2>지출 목록</h2>
        <button type="button" :disabled="loading || saving" @click="loadData">새로고침</button>
      </div>

      <p v-if="loading" role="status">조회 중입니다.</p>
      <p v-else-if="errorMessage">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="expenses.length === 0">등록된 지출이 없습니다.</p>

      <div v-if="!loading && expenses.length > 0" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">날짜</th>
              <th scope="col">제목</th>
              <th scope="col">카테고리</th>
              <th scope="col">유형</th>
              <th scope="col">금액</th>
              <th scope="col">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="expense in expenses" :key="expense.id">
              <td>{{ expense.expenseDate }}</td>
              <td>{{ expense.title }}</td>
              <td>{{ expense.categoryName }}</td>
              <td>
                {{ expense.expenseType === 'FIXED' ? '고정' : '변동' }}
              </td>
              <td class="amount">{{ expense.amount.toLocaleString('ko-KR') }}원</td>
              <td>
                <div class="button-group">
                  <button type="button" :disabled="saving || loading" @click="startEdit(expense)">
                    수정
                  </button>
                  <button
                    type="button"
                    :disabled="saving || loading"
                    @click="deleteExpense(expense)"
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

.memo-field {
  margin: 16px 0;
}

.error {
  color: #b91c1c;
}

.list-heading {
  display: flex;
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
.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.button-group button {
  white-space: nowrap;
}
</style>
