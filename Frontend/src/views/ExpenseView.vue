<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { Wallet, Plus, Pencil, Trash2, RefreshCw, List } from '@lucide/vue' // 생활비 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'

// 카테고리 선택 목록에서 사용하는 데이터다.
interface Category {
  id: number // 카테고리 ID
  name: string // 카테고리명
}

// 지출 목록 조회와 수정 폼에서 사용하는 데이터다.
interface Expense {
  id: number // 지출 ID
  categoryId: number // 카테고리 ID: 수정 시 기존 항목 선택에 사용
  categoryName: string // 카테고리명
  amount: number // 실제 지출 금액
  expenseDate: string // 지출일: YYYY-MM-DD
  expenseType: string // 지출 유형: FIXED(고정), VARIABLE(변동)
  paymentMethod: string | null // 결제 수단
  title: string // 지출 제목
  memo: string | null // 메모
}

// 조회 결과와 화면 처리 상태를 관리한다.
const categories = ref<Category[]>([]) // 선택 가능한 카테고리 목록
const expenses = ref<Expense[]>([]) // 조회한 지출 목록
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 완료 메시지
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록

// UTC 변환으로 날짜가 달라지지 않도록 로컬 날짜를 사용한다.
function today() {
  const date = new Date()
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

// 신규 등록과 수정 취소에 사용할 기본 입력값을 생성한다.
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

// 폼 입력값을 화면과 동기화한다.
const form = reactive(initialForm())

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)
const { canModify } = useDemoGuard(isDemo)

// 오류 원인은 개발자 도구에 기록하고, 화면에는 안내 메시지를 표시한다.
function showError(error: unknown) {
  console.error(error)
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 카테고리와 생활비를 조회한다. 비로그인이면 공통 함수가 샘플을 반환한다.
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  expenses.value = []
  categories.value = []
  isDemo.value = false

  try {
    const demo = createDemoData()

    // 서로 독립적인 카테고리와 지출 목록을 동시에 요청하고, 모두 성공하면 화면에 반영한다.
    const [categoryResult, expenseResult] = await Promise.all([
      requestWithDemo<Category[]>('/api/categories', () => demo.categories),
      requestWithDemo<Expense[]>('/api/expenses', () => demo.expenses),
    ])

    categories.value = categoryResult.data
    expenses.value = expenseResult.data
    // 둘 중 하나라도 체험 데이터이면 화면을 체험 상태로 처리해 변경 요청을 막는다.
    isDemo.value = categoryResult.isDemo || expenseResult.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 수정 중이면 PUT, 새 등록이면 POST로 저장한다.
async function saveExpense() {
  if (saving.value) return
  if (!canModify()) return

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

// 수정 상태를 해제하고 입력값을 신규 등록 상태로 되돌린다.
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

// 지출 삭제. 처리 중 중복 요청을 막는다.
async function deleteExpense(expense: Expense) {
  if (saving.value || loading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${expense.title}" 지출을 삭제하시겠습니까?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`/api/expenses/${expense.id}`, {
      method: 'DELETE',
    })

    // 수정 중이던 지출을 삭제했다면 폼도 초기화해 삭제된 항목을 다시 저장하지 않도록 한다.
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

// 화면이 처음 표시되면 카테고리와 지출 목록을 조회한다.
onMounted(loadData)
</script>

<template>
  <section class="crud-page expense-page">
    <header class="page-heading">
      <div class="page-icon">
        <Wallet :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>생활비</h1>
        <p>지출을 등록하고 사용 내역을 확인합니다.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험 중이에요. 현재 정보는 샘플 데이터입니다. 로그인하면 내 정보를 관리할 수 있어요.
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <form class="card" @submit.prevent="saveExpense">
      <div class="card-heading">
        <div class="heading-title">
          <component :is="editingId === null ? Plus : Pencil" :size="20" aria-hidden="true" />
          <h2>{{ editingId === null ? '지출 등록' : '지출 수정' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving || loading">
        <legend class="sr-only">지출 정보 입력</legend>

        <div class="form-grid">
          <label>
            제목 *
            <input
              v-model="form.title"
              required
              maxlength="200"
              placeholder="제목을 입력해주세요"
            />
          </label>

          <label>
            카테고리 *
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
            금액(원) *
            <input
              v-model="form.amount"
              type="number"
              min="0.01"
              step="0.01"
              required
              placeholder="금액을 입력해 주세요"
            />
          </label>

          <label>
            지출일 *
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
            <input
              v-model="form.paymentMethod"
              maxlength="50"
              placeholder="카드, 현금, 계좌이체 등"
            />
          </label>

          <label class="memo-field">
            메모
            <textarea
              v-model="form.memo"
              maxlength="2000"
              rows="3"
              placeholder="필요한 내용을 남겨 주세요"
            />
          </label>
        </div>

        <div class="form-actions">
          <button
            v-if="editingId !== null"
            type="button"
            class="btn btn-secondary"
            @click="resetForm"
          >
            수정 취소
          </button>

          <button type="submit" class="btn btn-primary" :disabled="categories.length === 0">
            <component :is="editingId === null ? Plus : Pencil" :size="16" aria-hidden="true" />
            {{ saving ? '저장 중…' : editingId === null ? '지출 등록' : '수정 저장' }}
          </button>
        </div>
      </fieldset>
    </form>

    <section class="card" aria-labelledby="expense-list-heading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2 id="expense-list-heading">지출 목록</h2>
          <span v-if="!loading" class="count-badge"> {{ expenses.length }}건 </span>
        </div>

        <button
          type="button"
          class="btn btn-secondary"
          :disabled="loading || saving"
          @click="loadData"
        >
          <RefreshCw :size="16" aria-hidden="true" />
          새로고침
        </button>
      </div>

      <p v-if="loading" class="empty-state" role="status">지출 내역을 불러오는 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="expenses.length === 0" class="empty-state">
        등록된 지출이 없습니다. 첫 지출을 등록해 보세요.
      </p>

      <div v-if="!loading && expenses.length > 0" class="table-wrap">
        <table class="data-table expense-table">
          <caption class="sr-only">
            등록된 지출 내역
          </caption>
          <thead>
            <tr>
              <th scope="col">날짜</th>
              <th scope="col">제목</th>
              <th scope="col">카테고리</th>
              <th scope="col">유형</th>
              <th scope="col" class="amount-cell">금액</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="expense in expenses"
              :key="expense.id"
              :class="{ 'editing-row': editingId === expense.id }"
            >
              <td class="date-cell">{{ expense.expenseDate }}</td>
              <td class="title-cell">{{ expense.title }}</td>
              <td>
                <span class="badge">{{ expense.categoryName }}</span>
              </td>
              <td>
                <span class="badge" :class="{ 'type-fixed': expense.expenseType === 'FIXED' }">
                  {{ expense.expenseType === 'FIXED' ? '고정' : '변동' }}
                </span>
              </td>
              <td class="amount-cell">{{ expense.amount.toLocaleString('ko-KR') }}원</td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="saving || loading"
                    :aria-label="`${expense.title} 수정`"
                    @click="startEdit(expense)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>
                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="saving || loading"
                    :aria-label="`${expense.title} 삭제`"
                    @click="deleteExpense(expense)"
                  >
                    <Trash2 :size="14" aria-hidden="true" />
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
.expense-table {
  min-width: 740px;
}

.expense-table .title-cell {
  min-width: 150px;
  max-width: 280px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

/* 고정 지출 유형을 파란색으로 구분한다. */
.expense-page .badge.type-fixed {
  background-color: #eff6ff;
  color: #2563eb;
}

.demo-notice {
  margin: 0 0 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #eaf1ff;
  color: #2563eb;
  font-size: 14px;
}
</style>
