<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { Repeat, Plus, Pencil, Trash2, List } from '@lucide/vue'
import { useDemoGuard } from '../composables/useDemoGuard'
import type { PageResponse } from '../types/page'
import { paginateDemo } from '../mocks/paginateDemo'
import Pagination from '../components/Pagination.vue'

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
  paid: boolean // 납부 완료 여부
  expenseId: number | null // 연결된 생활비 ID
  paidDate: string | null // 실제 납부일
}

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)
const { canModify } = useDemoGuard(isDemo)

// 월별 납부 예정 목록과 합계다.
interface MonthlyPayment {
  month: string // 조회 월: YYYY-MM
  totalAmount: number // 해당 월의 납부 예정 금액 합계
  items: PageResponse<MonthlyPaymentItem> // 해당 월에 적용되는 활성 항목 목록
}

// API 주소와 화면에서 사용하는 데이터를 관리한다.
const baseUrl = '/api/recurring-expenses' // 고정 지출 API 공통 주소
const categories = ref<Category[]>([]) // 카테고리 선택 목록
const expenses = ref<RecurringExpense[]>([]) // 활성·비활성을 포함한 관리 목록
const monthly = ref<MonthlyPayment | null>(null) // 월별 예정 목록과 합계
const page = ref(1)
const size = ref(5)
const monthlyPage = ref(1) // 월별 납부 목록은 관리 목록과 페이지를 별도로 관리한다.
const monthlySize = ref(5)
const totalCount = ref(0)
const totalPages = ref(0)
const searchTitle = ref('')
const searchCategoryId = ref<number | ''>('') // 빈 문자열이면 전체 카테고리를 조회한다.
const searchActive = ref<boolean | ''>('') // 빈 문자열: 전체, true: 사용 중, false: 비활성
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록
const saving = ref(false) // 조회·등록·수정·삭제·납부 처리 중 중복 실행을 막는다.
const loading = ref(false) // 관리 목록과 카테고리 조회가 성공하면 true가 된다.
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 완료 메시지
const paymentDates = reactive<Record<number, string>>({}) // 항목별로 입력한 실제 납부일을 보관한다.

// 검색 버튼으로 확정한 조건이다.
// 입력값만 바꾼 뒤 페이지를 이동해도 기존 검색 조건을 유지한다.
const appliedFilters = reactive({
  title: '',
  categoryId: '' as number | '',
  active: '' as boolean | '',
})

// 사용자 기기의 오늘 날짜를 YYYY-MM-DD 형식으로 반환한다.
function todayDate() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

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

// 오류를 화면에 표시한다.
function showError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 수정 상태를 해제하고 신규 등록 폼으로 되돌린다.
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

// 적용된 검색 조건으로 카테고리와 관리 목록을 갱신한다.
async function refreshManagement() {
  loading.value = false
  expenses.value = []
  totalCount.value = 0
  totalPages.value = 0

  const filters = { ...appliedFilters }
  const currentPage = page.value
  const pageSize = size.value
  const demo = createDemoData()

  const params = new URLSearchParams({
    title: filters.title,
    page: String(currentPage),
    size: String(pageSize),
  })

  if (filters.categoryId !== '') {
    params.set('categoryId', String(filters.categoryId))
  }

  if (filters.active !== '') {
    params.set('active', String(filters.active))
  }

  const [categoryResult, expenseResult] = await Promise.all([
    requestWithDemo<Category[]>('/api/categories', () => demo.categories),
    requestWithDemo<PageResponse<RecurringExpense>>(`${baseUrl}?${params.toString()}`, () => {
      const filtered = demo.recurringExpenses
        .filter(
          (item) =>
            (!filters.title || item.title.toLowerCase().includes(filters.title.toLowerCase())) &&
            (filters.categoryId === '' || item.categoryId === filters.categoryId) &&
            (filters.active === '' || item.active === filters.active),
        )
        .sort((a, b) => a.paymentDay - b.paymentDay || a.id - b.id)

      return paginateDemo(filtered, currentPage, pageSize)
    }),
  ])

  categories.value = categoryResult.data
  isDemo.value = isDemo.value || categoryResult.isDemo || expenseResult.isDemo

  const result = expenseResult.data

  // 삭제 등으로 현재 페이지가 사라졌으면 마지막 유효 페이지를 조회한다.
  const lastPage = Math.max(1, result.totalPages)
  if (currentPage > lastPage) {
    page.value = lastPage
    await refreshManagement()
    return
  }

  expenses.value = result.items
  page.value = result.page
  size.value = result.size
  totalCount.value = result.totalCount
  totalPages.value = result.totalPages
  loading.value = true
}

// 검색 조건을 확정하고 첫 페이지부터 조회한다.
async function searchExpenses() {
  if (saving.value) return

  if (editingId.value !== null && !window.confirm('수정을 취소하고 검색할까요?')) {
    return
  }

  if (editingId.value !== null) resetForm()

  Object.assign(appliedFilters, {
    title: searchTitle.value.trim(),
    categoryId: searchCategoryId.value,
    active: searchActive.value,
  })

  page.value = 1
  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await refreshManagement()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 적용된 검색 조건을 유지하면서 관리 목록의 페이지를 이동한다.
async function changePage(targetPage: number) {
  if (saving.value) return
  if (targetPage < 1 || targetPage > totalPages.value) return
  if (targetPage === page.value) return

  if (editingId.value !== null && !window.confirm('수정을 취소하고 다른 페이지로 이동할까요?')) {
    return
  }

  if (editingId.value !== null) resetForm()

  page.value = targetPage
  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await refreshManagement()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 선택한 월의 납부 목록을 페이지 단위로 조회한다.
async function refreshMonthly() {
  const month = selectedMonth.value
  const currentPage = monthlyPage.value
  const pageSize = monthlySize.value

  monthly.value = null

  Object.keys(paymentDates).forEach((id) => {
    delete paymentDates[Number(id)]
  })

  if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(month)) {
    throw new Error('조회할 월을 선택해 주세요.')
  }

  const year = Number(month.slice(0, 4))
  if (year < 1 || year > 9998) {
    throw new Error('지원하지 않는 연도입니다.')
  }

  const result = await requestWithDemo<MonthlyPayment>(
    `${baseUrl}/monthly/${month}?page=${currentPage}&size=${pageSize}`,
    () => {
      const sample = createDemoData(month).monthlyPayment

      const sorted = [...sample.items].sort(
        (a, b) => a.paymentDate.localeCompare(b.paymentDate) || a.id - b.id,
      )

      return {
        month: sample.month,
        totalAmount: sample.totalAmount,
        items: paginateDemo(sorted, currentPage, pageSize),
      }
    },
  )

  isDemo.value = isDemo.value || result.isDemo

  const resultPage = result.data.items
  const lastPage = Math.max(1, resultPage.totalPages)

  if (currentPage > lastPage) {
    monthlyPage.value = lastPage
    await refreshMonthly()
    return
  }

  monthly.value = result.data
  monthlyPage.value = resultPage.page
  monthlySize.value = resultPage.size
}

// 납부 완료는 POST, 취소는 DELETE로 처리한다.
async function changePayment(item: MonthlyPaymentItem) {
  if (saving.value || !monthly.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  // 선택창 값이 아니라 현재 표시된 목록의 월을 사용해 다른 월의 납부 처리를 방지한다.
  const month = monthly.value.month
  const cancel = item.paid
  const paymentDate = paymentDates[item.id] ?? todayDate()

  if (cancel) {
    if (!window.confirm(`"${item.title}" 납부를 취소할까요? 연결된 생활비도 삭제됩니다.`)) {
      return
    }
  } else if (!paymentDate || paymentDate > todayDate()) {
    errorMessage.value = '납부일은 오늘 또는 이전 날짜로 입력해 주세요.'
    return
  }

  saving.value = true

  try {
    const url = `${baseUrl}/${item.id}/payments/${month}`

    await request<null>(
      url,
      cancel
        ? { method: 'DELETE' }
        : {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ paymentDate }),
          },
    )

    notice.value = cancel
      ? '납부를 취소했습니다. 연결된 생활비도 삭제됐습니다.'
      : '납부 완료했습니다. 생활비에 반영됐습니다.'

    delete paymentDates[item.id]

    // 처리 결과를 다시 조회해 상태와 실제 납부일을 갱신한다.
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 최초 진입과 전체 새로고침 시 관리 목록과 예정 목록을 조회한다.
async function loadData() {
  if (saving.value) return

  saving.value = true
  isDemo.value = false
  errorMessage.value = ''
  notice.value = ''
  monthly.value = null

  try {
    await refreshManagement()
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 월을 변경할 때는 예정 목록만 다시 조회한다.
async function loadMonthly() {
  if (saving.value) return

  // 조회 월을 변경하면 첫 페이지부터 표시한다.
  monthlyPage.value = 1

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 현재 월을 유지하면서 납부 목록의 페이지를 이동한다.
async function changeMonthlyPage(targetPage: number) {
  if (saving.value || !monthly.value) return
  if (targetPage < 1 || targetPage > monthly.value.items.totalPages) return
  if (targetPage === monthlyPage.value) return

  monthlyPage.value = targetPage
  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await refreshMonthly()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 선택한 항목을 수정 폼에 채운다.
function startEdit(expense: RecurringExpense) {
  if (saving.value) return

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
  if (saving.value || !loading.value) return
  if (!canModify()) return

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
  saving.value = true

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
    saving.value = false
  }
}

// 확인을 받은 뒤 선택한 고정 지출 설정을 삭제한다.
async function deleteExpense(expense: RecurringExpense) {
  if (saving.value || !loading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${expense.title}" 고정 지출을 삭제하시겠습니까?`)) return

  saving.value = true
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
    saving.value = false
  }
}

// 금액에 천 단위 구분 기호와 원 단위를 표시한다.
function money(value: number) {
  return `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`
}

// 화면 진입 시 필요한 데이터를 조회한다.
onMounted(loadData)
</script>

<template>
  <section class="crud-page recurring-page">
    <header class="page-heading">
      <div class="page-icon">
        <Repeat :size="28" aria-hidden="true" />
      </div>
      <div class="heading">
        <h1>고정 지출</h1>
        <p>매달 반복되는 지출과 납부일을 관리하고 월별 예상 금액을 확인합니다.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험 중이에요. 현재 정보는 샘플 데이터입니다. 로그인하면 내 정보를 관리할 수 있어요.
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" class="message success-message" role="status">{{ notice }}</p>

    <form class="card" @submit.prevent="saveExpense">
      <div class="card-heading">
        <div class="heading-title">
          <component :is="editingId === null ? Plus : Pencil" :size="20" aria-hidden="true" />
          <h2>{{ editingId === null ? '고정 지출 등록' : '고정 지출 수정' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving || !loading">
        <legend class="sr-only">고정 지출 관리 입력</legend>
        <div class="form-grid">
          <label>
            항목명 *
            <input v-model="form.title" maxlength="200" required />
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
            매월 예정 금액 *
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
            매월 납부일 *
            <input v-model="form.paymentDay" type="number" min="1" max="31" step="1" required />
          </label>

          <label>
            시작 월 *
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
            사용 여부 *
            <select v-model="form.active">
              <option :value="true">사용</option>
              <option :value="false">중지</option>
            </select>
          </label>

          <label class="memo-field">
            메모
            <textarea v-model="form.memo" maxlength="2000" rows="3"></textarea>
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

          <button type="submit" class="btn btn-primary">
            <component :is="editingId === null ? Plus : Pencil" :size="16" aria-hidden="true" />
            {{ saving ? '저장 중…' : editingId === null ? '등록' : '수정 저장' }}
          </button>
        </div>
      </fieldset>
    </form>

    <section class="card" aria-labelledby="recurring-list-heading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2 id="recurring-list-heading">고정 지출 목록</h2>
          <span v-if="loading" class="count-badge">{{ totalCount }}건</span>
        </div>

        <!-- 관리 목록에만 카테고리·항목명·상태 조건을 적용한다. -->
        <form class="recurring-search" @submit.prevent="searchExpenses">
          <label>
            카테고리
            <select v-model="searchCategoryId" :disabled="saving">
              <option value="">전체</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <label>
            항목명
            <input
              v-model="searchTitle"
              type="search"
              placeholder="항목명 입력"
              :disabled="saving"
            />
          </label>

          <label>
            상태
            <select v-model="searchActive" :disabled="saving">
              <option value="">전체</option>
              <option :value="true">사용 중</option>
              <option :value="false">비활성</option>
            </select>
          </label>

          <button type="submit" class="btn btn-primary" :disabled="saving">검색</button>
        </form>
      </div>

      <p v-if="saving" class="empty-state" role="status">처리 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="loading && expenses.length === 0" class="empty-state">
        등록된 고정 지출이 없습니다.
      </p>

      <div v-if="loading && expenses.length > 0" class="table-wrap">
        <table class="data-table recurring-table">
          <caption class="sr-only">
            등록된 고정 지출
          </caption>
          <thead>
            <tr>
              <th scope="col">항목</th>
              <th scope="col">카테고리</th>
              <th scope="col" class="amount-cell">금액</th>
              <th scope="col">납부일</th>
              <th scope="col">적용 기간</th>
              <th scope="col">상태</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="expense in expenses"
              :key="expense.id"
              :class="{ 'editing-row': editingId === expense.id }"
            >
              <td class="title-cell">{{ expense.title }}</td>
              <td>{{ expense.categoryName }}</td>
              <td class="amount-cell">{{ money(expense.amount) }}</td>
              <td class="date-cell">매월 {{ expense.paymentDay }}일</td>
              <td class="date-cell">{{ expense.startMonth }} ~ {{ expense.endMonth ?? '계속' }}</td>
              <td>
                <span class="badge" :class="{ 'status-active': expense.active }">
                  {{ expense.active ? '사용' : '중지' }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="saving"
                    :aria-label="`${expense.title} 수정`"
                    @click="startEdit(expense)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>
                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="saving"
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
      <Pagination :page="page" :total-pages="totalPages" :disabled="saving" @change="changePage" />
    </section>

    <section class="card" aria-labelledby="monthly-heading">
      <div class="card-heading">
        <div class="heading-title">
          <Repeat :size="20" aria-hidden="true" />
          <h2 id="monthly-heading">월별 납부 현황</h2>
          <span v-if="monthly" class="count-badge"> {{ monthly.items.totalCount }}건 </span>
        </div>
      </div>

      <div class="form-grid monthly-filter">
        <label>
          조회 월
          <input
            v-model="selectedMonth"
            type="month"
            min="0001-01"
            max="9998-12"
            :disabled="saving"
            @change="loadMonthly"
          />
        </label>
      </div>

      <p class="monthly-description">
        선택한 월의 납부 상태를 확인합니다. 납부 완료 시 생활비에 반영되며, 합계는 미납 금액이 아닌
        전체 예정 금액입니다.
      </p>

      <p v-if="saving" class="empty-state" role="status">처리 중입니다.</p>

      <template v-else-if="monthly">
        <div class="monthly-summary">
          <span>납부 예정 합계</span>
          <strong>{{ money(monthly.totalAmount) }}</strong>
        </div>

        <p v-if="monthly.items.items.length === 0" class="empty-state">
          해당 월의 납부 예정 항목이 없습니다.
        </p>

        <div v-else class="table-wrap">
          <table class="data-table monthly-table">
            <caption class="sr-only">
              {{
                monthly.month
              }}
              납부 예정 목록
            </caption>
            <thead>
              <tr>
                <th scope="col">납부 예정일</th>
                <th scope="col">항목</th>
                <th scope="col">카테고리</th>
                <th scope="col" class="amount-cell">금액</th>
                <th scope="col">결제 수단</th>
                <th scope="col">납부 상태</th>
                <th scope="col">실제 납부일</th>
                <th scope="col">관리</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in monthly.items.items" :key="item.id">
                <td class="date-cell">{{ item.paymentDate }}</td>
                <td class="title-cell">{{ item.title }}</td>
                <td>
                  <span class="badge">{{ item.categoryName }}</span>
                </td>
                <td class="amount-cell">{{ money(item.amount) }}</td>
                <td>{{ item.paymentMethod || '—' }}</td>
                <td>
                  <span class="badge" :class="{ 'status-active': item.paid }">
                    {{ item.paid ? '납부 완료' : '미납' }}
                  </span>
                </td>

                <td>
                  <span v-if="item.paid" class="date-cell">
                    {{ item.paidDate }}
                  </span>

                  <input
                    v-else
                    class="payment-date-input"
                    type="date"
                    :value="paymentDates[item.id] ?? todayDate()"
                    :max="todayDate()"
                    :disabled="saving"
                    :aria-label="`${item.title} 실제 납부일`"
                    @input="paymentDates[item.id] = ($event.target as HTMLInputElement).value"
                  />
                </td>

                <td>
                  <button
                    type="button"
                    class="btn"
                    :class="item.paid ? 'btn-secondary' : 'btn-primary'"
                    :disabled="saving"
                    :aria-label="`${item.title} ${item.paid ? '납부 취소' : '납부 완료'}`"
                    @click="changePayment(item)"
                  >
                    {{ item.paid ? '납부 취소' : '납부 완료' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination
          :page="monthlyPage"
          :total-pages="monthly.items.totalPages"
          :disabled="saving"
          @change="changeMonthlyPage"
        />
      </template>

      <p v-else class="empty-state">
        월별 납부 예정 정보를 표시할 수 없습니다. 조회 월을 확인한 뒤 다시 조회해 주세요.
      </p>
    </section>
  </section>
</template>

<style scoped>
.recurring-table {
  min-width: 800px;
}

.recurring-table .title-cell {
  min-width: 150px;
  max-width: 280px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.recurring-page .badge.status-active {
  background-color: #ecfdf5;
  color: #047857;
}
/* 조회 월 입력 너비 */
.recurring-page .monthly-filter {
  grid-template-columns: minmax(0, 240px);
}

.monthly-description {
  margin: 12px 0 20px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

/* 월별 예정 합계 */
.monthly-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding: 20px;
  border-radius: 10px;
  background-color: #eff6ff;
}

.monthly-summary span {
  color: #475569;
  font-size: 14px;
}

.monthly-summary strong {
  color: #2563eb;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.monthly-table {
  min-width: 1050px;
}

.monthly-table .title-cell {
  min-width: 150px;
  max-width: 280px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.payment-date-input {
  width: 155px;
}

.demo-notice {
  margin: 0 0 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #eaf1ff;
  color: #2563eb;
  font-size: 14px;
}

/* 관리 목록 검색 조건을 오른쪽에 한 줄로 배치한다. */
.recurring-search {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.recurring-search label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.recurring-search input,
.recurring-search select {
  box-sizing: border-box;
  min-width: 0;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background-color: #fff;
  color: #1e293b;
  font: inherit;
  font-weight: 400;
}

.recurring-search input {
  width: 200px;
}

.recurring-search select {
  width: 130px;
}

.recurring-search input:focus-visible,
.recurring-search select:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.recurring-search input:disabled,
.recurring-search select:disabled {
  background-color: #f8fafc;
}

.recurring-search .btn {
  min-height: 44px;
}

@media (max-width: 640px) {
  .recurring-search {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .recurring-search label {
    display: grid;
    grid-template-columns: 70px minmax(0, 1fr);
  }

  .recurring-search input,
  .recurring-search select {
    width: 100%;
  }
}
</style>
