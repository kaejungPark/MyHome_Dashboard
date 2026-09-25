<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

// 백엔드 공통 응답 형식이다.
interface ApiResponse<T> {
  success: boolean // 요청 성공 여부
  data: T | null // 응답 데이터
  message: string | null // 오류 안내 메시지
}

// 카테고리 선택 목록이다.
interface Category {
  id: number // 카테고리 ID
  name: string // 카테고리명
}

// 등록된 고정 지출 정보다.
interface RecurringExpense {
  id: number // 고정 지출 ID
  categoryId: number // 카테고리 ID
  categoryName: string // 카테고리명
  title: string // 항목명
  amount: number // 매월 예정 금액
  paymentDay: number // 매월 납부일
  paymentMethod: string | null // 결제 수단
  startMonth: string // 시작 월
  endMonth: string | null // 종료 월
  active: boolean // 사용 여부
  memo: string | null // 메모
}

// 선택한 월에 납부할 항목이다.
interface MonthlyPaymentItem {
  id: number // 고정 지출 ID
  categoryId: number // 카테고리 ID
  categoryName: string // 카테고리명
  title: string // 항목명
  amount: number // 납부 예정 금액
  paymentDate: string // 말일 보정이 적용된 납부 예정일
  paymentMethod: string | null // 결제 수단
}

// 월별 납부 예정 목록과 합계다.
interface MonthlyPayment {
  month: string // 조회 월: YYYY-MM
  totalAmount: number // 해당 월의 납부 예정 금액 합계
  items: MonthlyPaymentItem[] // 해당 월에 적용되는 활성 항목 목록
}

// API 주소와 화면에서 사용하는 데이터를 관리한다.
const baseUrl = '/api/recurring-expenses' // 고정 지출 API 공통 주소
const categories = ref<Category[]>([]) // 카테고리 선택 목록
const expenses = ref<RecurringExpense[]>([]) // 활성·비활성을 포함한 관리 목록
const monthly = ref<MonthlyPayment | null>(null) // 월별 예정 목록과 합계
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록
const busy = ref(false) // 조회·저장·삭제 처리 중 여부
const ready = ref(false) // 관리 목록 조회 성공 여부
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 완료 메시지

// UTC 변환 없이 사용자 PC 기준 현재 월을 구한다.
function currentMonth() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

// 납부 예정 현황을 조회할 월이다. 처음에는 현재 월을 선택한다.
const selectedMonth = ref(currentMonth())

// 신규 등록 폼의 기본값이다.
function initialForm() {
  return {
    categoryId: '',
    title: '',
    amount: '',
    paymentDay: '1',
    paymentMethod: '',
    startMonth: currentMonth(),
    endMonth: '',
    active: true,
    memo: '',
  }
}

// 등록·수정 폼의 입력값을 화면과 동기화한다.
const form = reactive(initialForm())

// HTTP 상태와 공통 응답을 함께 확인한다.
async function request<T>(url: string, options?: RequestInit): Promise<T | null> {
  const response = await fetch(url, options)
  const result: ApiResponse<T> = await response.json()

  if (!response.ok || !result.success) {
    throw new Error(result.message ?? `요청 실패 (${response.status})`)
  }

  return result.data
}

// 오류를 화면에 표시한다.
function showError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 수정 상태를 해제하고 신규 등록 폼으로 되돌린다.
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

// 관리 목록과 카테고리를 갱신한다.
// 조회 실패 시 이전 목록으로 작업하지 못하도록 준비 상태를 해제한다.
async function refreshManagement() {
  ready.value = false

  const [categoryData, expenseData] = await Promise.all([
    request<Category[]>('/api/categories'),
    request<RecurringExpense[]>(baseUrl),
  ])

  categories.value = categoryData ?? []
  expenses.value = expenseData ?? []
  ready.value = true
}

// 선택한 월의 예정 목록을 갱신한다.
// 실제 날짜와 합계는 백엔드 계산 결과를 사용한다.
async function refreshMonthly() {
  monthly.value = null

  if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(selectedMonth.value)) {
    throw new Error('조회할 월을 선택해 주세요.')
  }

  const data = await request<MonthlyPayment>(`${baseUrl}/monthly/${selectedMonth.value}`)
  if (!data) throw new Error('월별 납부 예정 조회 결과가 없습니다.')

  monthly.value = data
}

// 최초 진입과 전체 새로고침 시 관리 목록과 예정 목록을 조회한다.
async function loadAll() {
  if (busy.value) return

  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  monthly.value = null

  try {
    await refreshManagement()
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    busy.value = false
  }
}

// 월을 변경할 때는 예정 목록만 다시 조회한다.
async function loadMonthly() {
  if (busy.value) return

  busy.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    busy.value = false
  }
}

// 선택한 항목을 수정 폼에 채운다.
function startEdit(expense: RecurringExpense) {
  if (busy.value) return

  editingId.value = expense.id
  errorMessage.value = ''
  notice.value = ''

  Object.assign(form, {
    categoryId: String(expense.categoryId),
    title: expense.title,
    amount: String(expense.amount),
    paymentDay: String(expense.paymentDay),
    paymentMethod: expense.paymentMethod ?? '',
    startMonth: expense.startMonth,
    endMonth: expense.endMonth ?? '',
    active: expense.active,
    memo: expense.memo ?? '',
  })

  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 등록은 POST, 수정은 PUT으로 같은 형식의 JSON을 전송한다.
async function saveExpense() {
  if (busy.value || !ready.value) return

  errorMessage.value = ''
  notice.value = ''

  const amount = Number(form.amount)
  const paymentDay = Number(form.paymentDay)

  // 필수 항목과 금액·납부일을 검사한다.
  if (
    !form.categoryId ||
    !form.title.trim() ||
    !form.startMonth ||
    !Number.isFinite(amount) ||
    amount <= 0 ||
    !Number.isInteger(paymentDay) ||
    paymentDay < 1 ||
    paymentDay > 31
  ) {
    errorMessage.value = '필수 항목, 양수 금액, 납부일 1~31을 확인해 주세요.'
    return
  }

  // YYYY-MM 형식이므로 문자열 비교로 월 순서를 확인할 수 있다.
  if (form.endMonth && form.endMonth < form.startMonth) {
    errorMessage.value = '종료 월은 시작 월보다 빠를 수 없습니다.'
    return
  }

  const id = editingId.value
  busy.value = true

  try {
    await request<null>(id === null ? baseUrl : `${baseUrl}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        categoryId: Number(form.categoryId),
        title: form.title.trim(),
        amount,
        paymentDay,
        paymentMethod: form.paymentMethod.trim() || null,
        startMonth: form.startMonth,
        endMonth: form.endMonth || null,
        active: form.active,
        memo: form.memo.trim() || null,
      }),
    })

    resetForm()
    notice.value = id === null ? '등록됐습니다.' : '수정됐습니다.'

    // 금액·기간·활성 상태 변경을 관리 목록과 월별 합계에 반영한다.
    monthly.value = null
    await refreshManagement()
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    busy.value = false
  }
}

// 확인을 받은 뒤 선택한 고정 지출 설정을 삭제한다.
async function deleteExpense(expense: RecurringExpense) {
  if (busy.value || !ready.value) return
  if (!window.confirm(`"${expense.title}" 고정 지출을 삭제하시겠습니까?`)) return

  busy.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`${baseUrl}/${expense.id}`, { method: 'DELETE' })

    if (editingId.value === expense.id) resetForm()

    notice.value = '삭제됐습니다.'
    monthly.value = null
    await refreshManagement()
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    busy.value = false
  }
}

// 금액에 천 단위 구분 기호와 원 단위를 표시한다.
function money(value: number) {
  return `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`
}

// 화면 진입 시 필요한 데이터를 조회한다.
onMounted(loadAll)
</script>

<template>
  <section>
    <div class="heading">
      <h1>고정 지출</h1>
      <button type="button" :disabled="busy" @click="loadAll">새로고침</button>
    </div>

    <p v-if="busy" role="status">처리 중입니다.</p>
    <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>

    <form class="panel" @submit.prevent="saveExpense">
      <h2>{{ editingId === null ? '고정 지출 등록' : '고정 지출 수정' }}</h2>

      <fieldset :disabled="busy || !ready">
        <div class="form-grid">
          <label>
            항목명
            <input v-model="form.title" maxlength="200" required />
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
            매월 예정 금액
            <input
              v-model="form.amount"
              type="text"
              inputmode="numeric"
              pattern="[0-9]+"
              placeholder="예: 500000"
              required
            />
          </label>

          <label>
            매월 납부일
            <input v-model="form.paymentDay" type="number" min="1" max="31" step="1" required />
          </label>

          <label>
            시작 월
            <input v-model="form.startMonth" type="month" min="0001-01" max="9998-12" required />
          </label>

          <label>
            종료 월 · 선택
            <input v-model="form.endMonth" type="month" :min="form.startMonth" max="9998-12" />
          </label>

          <label>
            결제 수단
            <input v-model="form.paymentMethod" maxlength="50" placeholder="카드, 계좌이체 등" />
          </label>

          <label>
            사용 여부
            <select v-model="form.active">
              <option :value="true">사용</option>
              <option :value="false">중지</option>
            </select>
          </label>
        </div>

        <label class="memo-field">
          메모
          <textarea v-model="form.memo" maxlength="2000" rows="3"></textarea>
        </label>

        <div class="button-group">
          <button type="submit" :disabled="categories.length === 0">
            {{ editingId === null ? '등록' : '수정 저장' }}
          </button>
          <button v-if="editingId !== null" type="button" @click="resetForm">수정 취소</button>
        </div>
      </fieldset>
    </form>

    <section class="panel">
      <h2>고정 지출 목록</h2>

      <p v-if="!ready">목록을 불러오지 못했거나 조회 중입니다.</p>
      <p v-else-if="expenses.length === 0">등록된 고정 지출이 없습니다.</p>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>항목</th>
              <th>카테고리</th>
              <th>금액</th>
              <th>납부일</th>
              <th>적용 기간</th>
              <th>상태</th>
              <th>관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="expense in expenses" :key="expense.id">
              <td>{{ expense.title }}</td>
              <td>{{ expense.categoryName }}</td>
              <td>{{ money(expense.amount) }}</td>
              <td>매월 {{ expense.paymentDay }}일</td>
              <td>{{ expense.startMonth }} ~ {{ expense.endMonth ?? '계속' }}</td>
              <td>{{ expense.active ? '사용' : '중지' }}</td>
              <td>
                <div class="button-group">
                  <button type="button" :disabled="busy" @click="startEdit(expense)">수정</button>
                  <button type="button" :disabled="busy" @click="deleteExpense(expense)">
                    삭제
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <h2>월별 납부 예정</h2>

      <label class="month-field">
        조회 월
        <input
          v-model="selectedMonth"
          type="month"
          min="0001-01"
          max="9998-12"
          :disabled="busy"
          @change="loadMonthly"
        />
      </label>

      <p>해당 월 전체의 예정 금액이며, 실제 납부 여부는 반영하지 않습니다.</p>

      <template v-if="monthly">
        <p>
          <strong>예정 합계: {{ money(monthly.totalAmount) }}</strong>
        </p>
        <p v-if="monthly.items.length === 0">해당 월의 납부 예정 항목이 없습니다.</p>

        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>납부 예정일</th>
                <th>항목</th>
                <th>카테고리</th>
                <th>금액</th>
                <th>결제 수단</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in monthly.items" :key="item.id">
                <td>{{ item.paymentDate }}</td>
                <td>{{ item.title }}</td>
                <td>{{ item.categoryName }}</td>
                <td>{{ money(item.amount) }}</td>
                <td>{{ item.paymentMethod ?? '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </section>
</template>

<style scoped>
.heading,
.button-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.heading {
  justify-content: space-between;
}

.heading h1 {
  margin: 0;
}

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

.month-field {
  max-width: 240px;
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

button {
  white-space: nowrap;
}

.error {
  color: #b91c1c;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
