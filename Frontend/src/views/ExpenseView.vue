<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { Wallet, Plus, Pencil, Trash2, List } from '@lucide/vue' // 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'
import type { PageResponse } from '../types/page'
import { paginateDemo } from '../mocks/paginateDemo'
import Pagination from '../components/Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { useErrorMessage } from '../composables/useErrorMessage'
import { today } from '../utils/date'

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

type ExpenseTypes = '' | 'FIXED' | 'VARIABLE' // 빈 문자열은 전체, FIXED는 고정 지출, VARIABLE은 변동 지출을 의미한다.

// 조회 결과와 화면 처리 상태를 관리한다.
const categories = ref<Category[]>([]) // 선택 가능한 카테고리 목록
const expenses = ref<Expense[]>([]) // 조회한 지출 목록
const searchTitle = ref('') // 검색창에 입력 중인 조건
const searchCategoryId = ref<number | ''>('') // 빈 문자열이면 전체 카테고리를 조회한다.
const searchExpenseType = ref<ExpenseTypes>('') // 조회용 지출유형
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const notice = ref('') // 처리 완료 메시지
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록
const { errorMessage, showError } = useErrorMessage() // 화면의 오류 메시지와 공통 오류 처리 함수를 사용한다.

// 생활비 목록을 한 페이지에 5건씩 표시한다.
// 페이지 번호·전체 건수·이동 가능 여부는 공통 페이지 처리 함수를 사용한다.
const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(5)

/**
 * 마지막으로 검색 버튼을 눌렀을 때의 조건을 저장
 * 페이지 이동이나 저장·삭제 후 재조회할 때도 이 조건을 사용
 * 검색창을 수정만 하고 조회하지 않은 값이 페이지 이동에 반영되는 것을 방지
 */
const appliedFilters = reactive({
  title: '',
  categoryId: '' as number | '',
  expenseType: '' as ExpenseTypes,
})

/**
 * 등록과 수정에서 함께 사용하는 반응형 입력 객체
 * 초기화할 때는 객체를 교체하지 않고 Object.assign으로 속성값을 갱신
 */
const form = reactive(initialForm())

const isDemo = ref(false) // 조회 결과에 체험 데이터가 포함되었는지 관리, 실제 데이터 접근 권한은 서버에서도 검증
const { canModify } = useDemoGuard(isDemo) // canModify는 체험 상태에서 등록·수정 저장·삭제 요청을 진행하지 않도록 안내

/**
 * 신규 등록과 수정 취소에 사용할 기본 입력값을 생성
 * 카테고리와 금액은 빈 입력을 표현할 수 있도록 문자열로 관리하고, 저장 요청을 만들 때 숫자로 변환
 */
function initialForm() {
  return {
    categoryId: '',
    amount: '',
    expenseDate: today(), // 새로 등록하거나 수정 취소할 때 지출일을 오늘로 설정한다.
    expenseType: 'VARIABLE',
    paymentMethod: '',
    title: '',
    memo: '',
  }
}

/**
 * 검색창의 입력값을 실제 조회 조건으로 확정하고 첫 페이지를 조회
 * 수정 중이라면 입력 취소 여부를 먼저 확인
 * 검색 결과의 페이지 수가 달라질 수 있으므로 기존 페이지 번호는 초기화
 */
async function searchExpenses() {
  if (loading.value || saving.value) return

  if (editingId.value !== null && !window.confirm('수정을 취소하고 검색할까요?')) {
    return
  }

  if (editingId.value !== null) resetForm()

  // 제목의 앞뒤 공백을 제거하고, 현재 입력값을 적용된 검색 조건에 복사
  Object.assign(appliedFilters, {
    title: searchTitle.value.trim(),
    categoryId: searchCategoryId.value,
    expenseType: searchExpenseType.value,
  })

  resetPage() // 새로운 검색은 첫 페이지부터 시작한다.
  notice.value = ''
  await loadData() // 확정한 검색 조건으로 지출 목록을 조회한다.
}

/**
 * 적용된 검색 조건을 유지하면서 선택한 페이지를 조회
 * 조회·저장 중이거나 이동할 수 없는 페이지이면 요청하지 않는다.
 * 수정 중인 입력은 사용자 확인 후 초기화
 */
async function changePage(targetPage: number) {
  if (loading.value || saving.value) return
  if (!canMoveTo(targetPage)) return

  if (editingId.value !== null && !window.confirm('수정을 취소하고 다른 페이지로 이동할까요?')) {
    return
  }

  if (editingId.value !== null) {
    resetForm()
  }

  notice.value = ''
  page.value = targetPage // 목표 페이지를 설정한 뒤 기존 검색 조건으로 목록을 다시 조회
  await loadData()
}

/**
 * 필수 입력과 금액을 검증한 뒤 지출을 등록하거나 수정
 * editingId가 없으면 POST로 등록하고, 있으면 해당 ID에 PUT으로 수정
 * 저장 성공 후 입력 폼을 초기화하고 적용된 검색 조건으로 목록을 다시 조회
 * 저장 요청이 실패하면 입력값을 유지
 */
async function saveExpense() {
  if (saving.value || loading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  // 입력한 금액을 숫자로 변환하고 필수 입력 여부를 검사
  // NaN·Infinity 등 유효하지 않은 숫자와 0 이하의 금액은 저장하지 않는다.
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

  // 요청 시작 시 수정 대상 ID를 보관
  // 저장 후 폼을 초기화해도 등록·수정 여부에 맞는 완료 문구를 표시할 수 있다.
  const id = editingId.value
  saving.value = true

  try {
    await request<null>(id === null ? '/api/expenses' : `/api/expenses/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      // 서버 요청 타입에 맞게 숫자를 변환하고 문자열의 앞뒤 공백을 제거, 선택 입력인 결제 수단과 메모는 비어 있으면 null로 전달
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

    resetForm() // 저장 성공 후 폼을 초기화하고 최신 목록을 조회한다.
    notice.value = id === null ? '등록됐습니다.' : '수정됐습니다.'
    await loadData() // 저장된 내용과 검색 결과의 건수·페이지 변화를 반영하기 위해 재조회
  } catch (error: unknown) {
    // 저장 실패 시 입력 내용을 유지
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 수정 대상 ID를 해제하고 입력값을 신규 등록 상태로 초기화
 * 기존 reactive 객체를 유지하면서 각 속성의 값만 변경
 */
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

/**
 * 선택한 지출을 수정 대상으로 지정하고 기존 값을 입력 폼에 복사
 * 폼에서 사용하는 타입에 맞춰 카테고리 ID와 금액을 문자열로 변환
 * nullable 필드는 빈 문자열로 바꾸고 화면 상단의 입력 영역으로 이동
 */
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

/**
 * 사용자 확인 후 선택한 지출을 삭제
 * 조회·저장 중에는 중복 요청을 막고, 체험 상태에서는 삭제를 진행하지 않는다.
 * 삭제 성공 후 수정 대상과 목록을 갱신
 * 삭제로 현재 페이지가 사라지는 경우는 loadData에서 보정
 */
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

/**
 * 카테고리와 검색 조건에 맞는 지출 목록을 조회
 * 비로그인 유저는 requestWithDemo의 체험 데이터로 대체
 * 실제 목록과 체험 목록 모두 PageResponse 형식으로 처리
 * 삭제 등으로 현재 페이지가 사라지면 마지막 유효 페이지를 다시 조회
 */
async function loadData() {
  // 진행 중인 목록 조회가 있으면 중복 요청을 막는다.
  // 저장·삭제 처리 내부에서도 재조회하므로 saving 상태로는 차단하지 않는다.
  if (loading.value) return

  loading.value = true // 이전 목록과 건수를 비우고 로딩 상태로 전환, 현재 페이지 번호와 적용된 검색 조건은 유지
  errorMessage.value = ''
  expenses.value = []
  resetTotals()

  try {
    const filters = { ...appliedFilters } // 이번 조회에 사용할 조건과 페이지 크기를 복사, 마지막 페이지 보정으로 재요청하더라도 같은 조건을 사용
    const pageSize = size.value

    const demo = createDemoData() // 조회에서 사용할 체험 데이터를 생성, 실제 API 대신 체험 데이터를 반환할 때 아래 콜백에서 사용

    // 지정한 페이지를 조회하는 내부 함수, 최초 조회와 마지막 페이지 보정에 같은 요청 구성을 재사용
    const fetchExpensePage = (targetPage: number) => {
      // 검색 조건과 페이지 정보를 쿼리 문자열로 구성, URLSearchParams가 공백·한글·특수문자를 URL에 맞게 인코딩
      const params = new URLSearchParams({
        title: filters.title,
        expenseType: filters.expenseType,
        page: String(targetPage),
        size: String(pageSize),
      })

      // 전체 카테고리를 선택한 경우 categoryId를 보내지 않는다. 선택한 경우에만 숫자 ID를 문자열로 변환해 전달
      if (filters.categoryId !== '') {
        params.set('categoryId', String(filters.categoryId))
      }

      return requestWithDemo<PageResponse<Expense>>(`/api/expenses?${params.toString()}`, () => {
        /**
         * 체험 목록에도 제목·카테고리·지출 유형 조건을 적용
         * 제목은 대소문자를 구분하지 않는 포함 검색이며, 빈 조건은 전체로 처리
         * 지출일 내림차순으로 정렬하고 같은 날짜는 ID 내림차순으로 순서를 정한다.
         */
        const filtered = demo.expenseList
          .filter(
            (item) =>
              (!filters.title || item.title.toLowerCase().includes(filters.title.toLowerCase())) &&
              (filters.categoryId === '' || item.categoryId === filters.categoryId) &&
              (!filters.expenseType || item.expenseType === filters.expenseType),
          )
          .sort((a, b) => b.expenseDate.localeCompare(a.expenseDate) || b.id - a.id)

        // 필터링과 정렬을 마친 전체 결과에서 요청한 페이지의 항목만 추출, 전체 건수와 페이지 수도 필터링된 결과를 기준으로 계산
        return paginateDemo(filtered, targetPage, pageSize)
      })
    }

    // 서로 의존하지 않는 카테고리와 지출 목록을 병렬로 조회, 두 요청이 모두 성공한 뒤 화면에 반영하며, 하나라도 실패하면 catch에서 처리
    const [categoryResult, firstExpenseResult] = await Promise.all([
      requestWithDemo<Category[]>('/api/categories', () => demo.categories),
      fetchExpensePage(page.value),
    ])

    let expenseResult = firstExpenseResult
    // 결과가 0건이면 전체 페이지 수는 0이지만, 요청 페이지 번호는 최소 1로 유지
    const lastPage = Math.max(1, expenseResult.data.totalPages)

    // 마지막 페이지의 항목 삭제 등으로 현재 페이지가 범위를 벗어나면 같은 검색 조건으로 마지막 유효 페이지를 한 번 더 조회
    if (page.value > lastPage) {
      page.value = lastPage
      expenseResult = await fetchExpensePage(lastPage)
    }

    categories.value = categoryResult.data

    const result = expenseResult.data

    // 목록 데이터는 화면에 저장하고, 페이지 정보는 공통 함수에 반영
    expenses.value = result.items
    applyPage(result)

    // 카테고리 또는 지출 목록 중 하나라도 체험 데이터이면 체험 상태로 표시
    isDemo.value = categoryResult.isDemo || expenseResult.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false // 성공 여부와 관계없이 로딩을 해제해 다시 조회
  }
}

// 화면이 처음 표시되면 카테고리와 지출 목록을 조회
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

    <!-- 하나의 입력 폼을 등록과 수정에 함께 사용하며, editingId로 현재 작업을 구분한다. -->
    <form class="card" @submit.prevent="saveExpense">
      <div class="card-heading">
        <div class="heading-title">
          <component :is="editingId === null ? Plus : Pencil" :size="20" aria-hidden="true" />
          <h2>{{ editingId === null ? '지출 등록' : '지출 수정' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <!-- 조회 또는 저장 중에는 입력값이 바뀌거나 중복 제출되지 않도록 입력 영역을 비활성화한다. -->
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
          <span v-if="!loading" class="count-badge"> {{ totalCount }}건 </span>
        </div>

        <!-- 검색 버튼으로 확정한 조건을 목록에 적용한다. -->
        <form class="expense-search" @submit.prevent="searchExpenses">
          <label>
            카테고리
            <select v-model="searchCategoryId" :disabled="loading || saving">
              <option value="">전체</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <label>
            제목
            <input
              v-model="searchTitle"
              type="search"
              placeholder="지출 제목 입력"
              :disabled="loading || saving"
            />
          </label>

          <label>
            지출 유형
            <select v-model="searchExpenseType" :disabled="loading || saving">
              <option value="">전체</option>
              <option value="FIXED">고정 지출</option>
              <option value="VARIABLE">변동 지출</option>
            </select>
          </label>

          <button type="submit" class="btn btn-primary" :disabled="loading || saving">검색</button>
        </form>
      </div>

      <!-- 조회 중, 오류 발생, 검색 결과 없음 상태를 순서대로 구분해 안내한다. -->
      <p v-if="loading" class="empty-state" role="status">지출 내역을 불러오는 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="expenses.length === 0" class="empty-state">조회 조건에 맞는 지출이 없습니다.</p>

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
      <!-- 목록 카드 내부에서 페이지 이동 버튼을 표시한다. -->
      <Pagination
        :page="page"
        :total-pages="totalPages"
        :disabled="loading || saving"
        @change="changePage"
      />
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

/* 검색 필터와 버튼을 목록 상단 오른쪽에 배치한다. */
.expense-search {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.expense-search label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.expense-search input,
.expense-search select {
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

.expense-search input {
  width: 200px;
}

.expense-search select {
  width: 130px;
}

.expense-search input:focus-visible,
.expense-search select:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.expense-search input:disabled,
.expense-search select:disabled {
  background-color: #f8fafc;
}

.expense-search .btn {
  min-height: 44px;
}

/* 좁은 화면에서는 검색 항목을 세로로 배치해 입력창이 잘리지 않도록 한다. */
@media (max-width: 640px) {
  .expense-search {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .expense-search label {
    display: grid;
    grid-template-columns: 70px minmax(0, 1fr);
  }

  .expense-search input,
  .expense-search select {
    width: 100%;
  }
}
</style>
