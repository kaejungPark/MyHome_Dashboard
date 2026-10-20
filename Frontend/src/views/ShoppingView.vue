<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { ShoppingCart, Plus, Pencil, Trash2, List } from '@lucide/vue'
import { useDemoGuard } from '../composables/useDemoGuard'
import type { PageResponse } from '../types/page'
import { paginateDemo } from '../mocks/paginateDemo'
import Pagination from '../components/Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { useErrorMessage } from '../composables/useErrorMessage'

interface ShoppingItem {
  id: number // 장보기 항목 ID, PK·자동 증가
  name: string // 구매할 물품명
  quantity: number // 구매할 수량, 0보다 큰 값
  unit: string // 단위: 개·팩·kg 등
  isCompleted: boolean // 구매 완료 여부
  completedDate: string | null // 실제 구매일, 미구매이면 null
  purchaseDueDate: string | null // 구매 예정일, 미지정이면 null
  memo: string | null // 추가 메모
}

const shoppingItems = ref<ShoppingItem[]>([]) // 현재 페이지의 장보기 목록
const searchName = ref('')
const searchCompleted = ref<'' | boolean>('')
const searchPurchaseDueDate = ref('')

const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const notice = ref('') // 처리 완료 메시지
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록
const { errorMessage, showError } = useErrorMessage() // 화면의 오류 메시지와 공통 오류 처리 함수를 사용한다.

const baseUrl = '/api/shopping-items'

// 장보기 목록의 페이지 번호·크기·전체 건수와 공통 페이징 함수를 사용한다.
const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(5)

/**
 * 마지막으로 검색 버튼을 눌렀을 때의 조건을 저장
 * 페이지 이동이나 저장·삭제 후 재조회할 때도 이 조건을 사용
 * 검색창을 수정만 하고 조회하지 않은 값이 페이지 이동에 반영되는 것을 방지
 */
const appliedFilters = reactive({
  name: '',
  isCompleted: '' as '' | boolean,
  purchaseDueDate: '',
})

/**
 * 등록과 수정에서 함께 사용하는 반응형 입력 객체
 * 초기화할 때는 객체를 교체하지 않고 Object.assign으로 속성값을 갱신
 */
const form = reactive(initialForm())

const isDemo = ref(false) // 조회 결과에 체험 데이터가 포함되었는지 관리, 실제 데이터 접근 권한은 서버에서도 검증
const { canModify } = useDemoGuard(isDemo) // canModify는 체험 상태에서 등록·수정 저장·삭제 요청을 진행하지 않도록 안내

// 선택 입력은 빈 문자열로 초기화하고, 저장할 때 null로 변환한다.
function initialForm() {
  return {
    name: '',
    quantity: 1,
    unit: '',
    purchaseDueDate: '',
    memo: '',
  }
}

/**
 * 선택한 식재료를 수정 대상으로 지정하고 기존 값을 입력 폼에 복사
 */
function startEdit(item: ShoppingItem) {
  editingId.value = item.id
  errorMessage.value = ''
  notice.value = ''

  // 선택 입력의 null은 입력창에서 사용할 빈 문자열로 변환한다.
  Object.assign(form, {
    name: item.name,
    quantity: item.quantity,
    unit: item.unit,
    purchaseDueDate: item.purchaseDueDate ?? '',
    memo: item.memo ?? '',
  })

  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/**
 * 입력한 검색 조건을 확정하고 첫 페이지부터 조회한다.
 * 수정 중이라면 사용자 확인 후 수정 폼을 초기화한다.
 */
async function searchShopping() {
  if (loading.value || saving.value) return

  if (editingId.value !== null && !window.confirm('수정을 취소하고 검색할까요?')) {
    return
  }

  if (editingId.value !== null) resetForm()

  // 물품명의 앞뒤 공백을 제거하고, 현재 입력값을 적용된 검색 조건에 복사
  Object.assign(appliedFilters, {
    name: searchName.value.trim(),
    isCompleted: searchCompleted.value,
    purchaseDueDate: searchPurchaseDueDate.value,
  })

  resetPage() // 새로운 검색은 첫 페이지부터 시작하고 이전 처리 완료 안내
  notice.value = ''
  await loadData() // 확정한 검색 조건으로 장보기 목록을 조회한다.
}

// 마지막으로 확정한 검색 조건을 유지하면서 선택한 페이지를 조회한다.
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
 * 수정 대상 ID를 해제하고 입력값을 신규 등록 상태로 초기화
 * 기존 reactive 객체를 유지하면서 각 속성의 값만 변경
 */
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

/**
 * 입력값을 검증하고 수정 대상 ID의 유무에 따라 등록 또는 수정을 요청한다.
 * 저장에 성공하면 입력 폼을 초기화하고 현재 검색 조건으로 목록을 다시 조회한다.
 * 저장 요청에 실패하면 작성 중인 입력값을 유지한다.
 */
async function saveShopping() {
  if (saving.value || loading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  const name = form.name.trim()
  const quantityText = String(form.quantity).trim()
  const quantity = Number(quantityText)
  const unit = form.unit.trim()
  const memo = form.memo.trim()

  if (!name || !unit) {
    errorMessage.value = '구매 물품명과 단위를 입력해 주세요.'
    return
  }

  if (name.length > 100 || unit.length > 20 || memo.length > 500) {
    errorMessage.value = '구매 물품명은 100자, 단위는 20자, 메모는 500자까지 입력할 수 있습니다.'
    return
  }

  // DB의 DECIMAL(10, 2)에 맞춰 양수·최댓값·소수 자릿수를 확인한다.
  if (
    !Number.isFinite(quantity) ||
    quantity <= 0 ||
    quantity > 99999999.99 ||
    !/^\d+(\.\d{1,2})?$/.test(quantityText)
  ) {
    errorMessage.value =
      '수량은 0보다 크고 99,999,999.99 이하이며, 소수 둘째 자리까지 입력해 주세요.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    await request<null>(id === null ? baseUrl : `${baseUrl}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      // 서버 요청 타입에 맞게 숫자를 변환하고 문자열의 앞뒤 공백을 제거, 선택 입력인 구매 예정 날짜·메모는 비어 있으면 null로 전달
      body: JSON.stringify({
        name,
        quantity,
        unit,
        purchaseDueDate: form.purchaseDueDate || null,
        memo: memo || null,
      }),
    })

    resetForm() // 저장 성공 후 폼을 초기화하고 최신 목록을 조회한다.
    notice.value = id === null ? '등록됐습니다.' : '수정됐습니다.'
    await loadData() // 저장된 내용과 검색 결과의 건수·페이지 변화를 반영하기 위해
  } catch (error: unknown) {
    // 저장 요청이 실패하면 입력 내용을 유지한다.
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 사용자 확인 후 선택한 미구매 항목을 구매 완료 처리한다.
 * 실제 구매일은 서버에서 설정하므로 요청 본문은 보내지 않는다.
 * 성공하면 해당 항목의 수정 폼을 초기화하고 목록을 다시 조회한다.
 */
async function completeShopping(item: ShoppingItem) {
  if (saving.value || loading.value) return
  if (!canModify()) return
  // 이미 구매 완료된 항목은 중복 처리하지 않는다.
  if (item.isCompleted) return
  if (!window.confirm(`"${item.name}" 항목을 구매 완료 처리할까요?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    // 구매 상태와 실제 구매일은 서버에서 변경하므로 요청 본문은 보내지 않는다.
    await request<null>(`${baseUrl}/${item.id}/completed`, { method: 'PUT' })

    // 상태를 변경한 항목을 수정 중이었다면 입력 폼을 초기화한다.
    if (editingId.value === item.id) resetForm()

    notice.value = '구매 완료 처리됐습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 사용자 확인 후 구매 완료 상태를 취소한다.
 * 서버에서 구매 여부를 false로, 실제 구매일을 null로 변경한다.
 * 구매 예정일은 유지하며, 성공하면 수정 폼과 목록을 갱신한다.
 */
async function cancelCompletion(item: ShoppingItem) {
  if (saving.value || loading.value) return
  if (!canModify()) return

  // 미구매 항목은 구매 완료를 취소할 수 없다.
  if (!item.isCompleted) return
  if (!window.confirm(`"${item.name}" 항목의 구매 완료를 취소할까요?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    // 구매 상태와 실제 구매일은 서버에서 변경하므로 요청 본문은 보내지 않는다.
    await request<null>(`${baseUrl}/${item.id}/cancel`, { method: 'PUT' })

    // 상태를 변경한 항목을 수정 중이었다면 입력 폼을 초기화한다.
    if (editingId.value === item.id) resetForm()

    notice.value = '구매 완료가 취소됐습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 사용자 확인 후 선택한 장보기 항목을 삭제한다.
 * 삭제한 항목을 수정 중이었다면 폼을 초기화하고 목록을 다시 조회한다.
 */
async function deleteShopping(item: ShoppingItem) {
  if (saving.value || loading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${item.name}" 장보기를 삭제할까요?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`${baseUrl}/${item.id}`, { method: 'DELETE' })

    if (editingId.value === item.id) resetForm()

    notice.value = '장보기가 삭제됐습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 확정된 검색 조건과 페이지 번호로 장보기 목록을 조회한다.
 * 체험 데이터에도 동일한 검색 조건을 적용한 후 페이지 단위로 나눈다.
 * 삭제 등으로 현재 페이지가 마지막 페이지를 초과하면 마지막 페이지를 다시 조회한다.
 */
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  shoppingItems.value = []
  resetTotals()

  try {
    const filters = { ...appliedFilters } // 이번 조회에 사용할 조건과 페이지 크기를 복사, 마지막 페이지 보정으로 재요청하더라도 같은 조건을 사용
    const pageSize = size.value

    const demo = createDemoData() // 조회에서 사용할 체험 데이터를 생성, 실제 API 대신 체험 데이터를 반환할 때 아래 콜백에서 사용

    const fetchShoppingPage = (targetPage: number) => {
      const params = new URLSearchParams({
        name: filters.name,
        isCompleted: String(filters.isCompleted),
        purchaseDueDate: filters.purchaseDueDate,
        page: String(targetPage),
        size: String(pageSize),
      })

      return requestWithDemo<PageResponse<ShoppingItem>>(`${baseUrl}?${params.toString()}`, () => {
        /**
         * 체험 목록에도 물품명·구매 완료 여부·구매 예정일 조건을 적용
         * 물품명은 대소문자를 구분하지 않는 포함 검색이며, 빈 조건은 전체로 처리
         */
        const filtered = demo.shoppingItems.filter(
          (shoppingItems) =>
            (!filters.name ||
              shoppingItems.name.toLocaleLowerCase().includes(filters.name.toLowerCase())) &&
            (filters.isCompleted === '' || shoppingItems.isCompleted === filters.isCompleted) &&
            (!filters.purchaseDueDate || shoppingItems.purchaseDueDate === filters.purchaseDueDate),
        )

        // 필터링한 전체 결과에서 요청한 페이지의 항목만 추출, 전체 건수와 페이지 수도 필터링된 결과를 기준으로 계산
        return paginateDemo(filtered, targetPage, pageSize)
      })
    }

    let result = await fetchShoppingPage(page.value)

    const lastPage = Math.max(1, result.data.totalPages)

    if (page.value > lastPage) {
      page.value = lastPage
      result = await fetchShoppingPage(lastPage)
    }

    shoppingItems.value = result.data.items
    applyPage(result.data)
    isDemo.value = result.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 화면이 처음 표시되면 장보기 목록을 조회한다.
onMounted(loadData)
</script>

<template>
  <section class="crud-page shopping-page">
    <header class="page-heading">
      <div class="page-icon">
        <ShoppingCart :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>장보기</h1>
        <p>구매할 물품과 예정일을 관리합니다.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험용 데이터입니다. 등록·수정하려면 로그인해 주세요.
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>

    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <!-- 등록·수정 입력 영역 -->
    <form class="card" @submit.prevent="saveShopping">
      <div class="card-heading">
        <div class="heading-title">
          <Pencil v-if="editingId !== null" :size="20" aria-hidden="true" />
          <Plus v-else :size="20" aria-hidden="true" />
          <h2>{{ editingId !== null ? '장보기 수정' : '장보기 등록' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving || loading || isDemo">
        <legend class="sr-only">장보기 정보 입력</legend>

        <div class="form-grid">
          <label>
            <span>물품명 *</span>
            <input
              v-model.trim="form.name"
              type="text"
              maxlength="100"
              placeholder="예: 우유"
              required
            />
          </label>

          <label>
            <span>수량 *</span>
            <input
              v-model.number="form.quantity"
              type="number"
              min="0.01"
              max="99999999.99"
              step="0.01"
              required
            />
          </label>

          <label>
            <span>단위 *</span>
            <input
              v-model.trim="form.unit"
              type="text"
              maxlength="20"
              placeholder="예: 개, 팩, kg"
              required
            />
          </label>

          <label>
            <span>구매 예정일</span>
            <input v-model="form.purchaseDueDate" type="date" min="0001-01-01" max="9999-12-31" />
            <small class="helper-text">
              예정일을 입력하지 않은 항목은 날짜 기준 알림에서 제외됩니다.
            </small>
          </label>

          <label class="memo-field">
            <span>메모</span>
            <textarea
              v-model.trim="form.memo"
              rows="3"
              maxlength="500"
              placeholder="브랜드나 구매 장소 등을 적어 주세요."
            ></textarea>
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
            <Pencil v-if="editingId !== null" :size="16" aria-hidden="true" />
            <Plus v-else :size="16" aria-hidden="true" />
            {{ saving ? '처리 중...' : editingId !== null ? '수정 저장' : '등록' }}
          </button>
        </div>
      </fieldset>
    </form>

    <!-- 목록 제목과 검색 조건을 같은 영역에 배치한다. -->
    <section class="card" aria-labelledby="shopping-list-heading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2 id="shopping-list-heading">장보기 목록</h2>
          <span v-if="!loading" class="count-badge">{{ totalCount }}건</span>
        </div>

        <form class="shopping-search" @submit.prevent="searchShopping">
          <label class="search-field">
            <span>물품명</span>
            <input
              v-model.trim="searchName"
              type="text"
              maxlength="100"
              placeholder="물품명 검색"
              :disabled="loading || saving"
            />
          </label>

          <label class="search-field status-field">
            <span>구매 여부</span>
            <select v-model="searchCompleted" :disabled="loading || saving">
              <option value="">전체</option>
              <option :value="false">미구매</option>
              <option :value="true">구매 완료</option>
            </select>
          </label>

          <label class="search-field">
            <span>구매 예정일</span>
            <input
              v-model="searchPurchaseDueDate"
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              :disabled="loading || saving"
            />
          </label>

          <button type="submit" class="btn btn-primary" :disabled="loading || saving">조회</button>
        </form>
      </div>

      <p v-if="loading" class="empty-state" role="status">장보기 목록을 불러오는 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="shoppingItems.length === 0" class="empty-state">
        조회된 장보기 항목이 없습니다.
      </p>

      <div v-if="!loading && shoppingItems.length > 0" class="table-wrap">
        <table class="data-table shopping-table">
          <caption class="sr-only">
            장보기 목록과 구매 상태
          </caption>

          <thead>
            <tr>
              <th scope="col">물품명</th>
              <th scope="col" class="amount-cell">수량</th>
              <th scope="col">구매 예정일</th>
              <th scope="col">실제 구매일</th>
              <th scope="col">상태</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="item in shoppingItems"
              :key="item.id"
              :class="{ 'editing-row': editingId === item.id }"
            >
              <td class="shopping-name-cell">
                <span class="shopping-name">{{ item.name }}</span>
                <span v-if="item.memo" class="shopping-memo">
                  {{ item.memo }}
                </span>
              </td>

              <td class="amount-cell">{{ item.quantity }} {{ item.unit }}</td>
              <td class="date-cell">{{ item.purchaseDueDate ?? '미지정' }}</td>
              <td class="date-cell">{{ item.completedDate ?? '-' }}</td>

              <td>
                <span
                  class="badge"
                  :class="item.isCompleted ? 'shopping-completed' : 'shopping-pending'"
                >
                  {{ item.isCompleted ? '구매 완료' : '미구매' }}
                </span>
              </td>

              <td class="manage-cell">
                <div class="row-actions">
                  <button
                    v-if="!item.isCompleted"
                    type="button"
                    class="btn btn-complete"
                    :disabled="saving || loading || isDemo"
                    @click="completeShopping(item)"
                  >
                    구매 완료
                  </button>

                  <button
                    v-else
                    type="button"
                    class="btn btn-secondary"
                    :disabled="saving || loading || isDemo"
                    @click="cancelCompletion(item)"
                  >
                    완료 취소
                  </button>

                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="saving || loading || isDemo"
                    @click="startEdit(item)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>

                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="saving || loading || isDemo"
                    @click="deleteShopping(item)"
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
/* 입력창·카드·버튼·테이블의 기본 모양은 common.css를 사용한다. */
.helper-text {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.6;
}

.demo-notice {
  margin: 0 0 20px;
  padding: 14px 16px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background-color: #eff6ff;
  color: #1d4ed8;
  font-size: 14px;
  line-height: 1.6;
}

/* 검색 조건은 목록 제목 오른쪽에 배치하고 공간이 부족하면 줄바꿈한다. */
.shopping-search {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-left: auto;
}

.shopping-search .search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.shopping-search input,
.shopping-search select {
  box-sizing: border-box;
  width: 180px;
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

.shopping-search .status-field select {
  width: 120px;
}

.shopping-search input:focus-visible,
.shopping-search select:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.shopping-search input:disabled,
.shopping-search select:disabled {
  background-color: #f8fafc;
  cursor: not-allowed;
}

.shopping-search .btn {
  min-height: 44px;
}

/* 좁은 화면에서는 표 영역 안에서 가로로 스크롤한다. */
.shopping-table {
  min-width: 940px;
}

.shopping-name-cell {
  min-width: 180px;
  max-width: 280px;
  overflow-wrap: anywhere;
}

.shopping-name {
  display: block;
  font-weight: 600;
}

.shopping-memo {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

/* 구매 여부에 따른 상태 색상 */
.shopping-page .badge.shopping-pending {
  background-color: #fff7ed;
  color: #c2410c;
}

.shopping-page .badge.shopping-completed {
  background-color: #ecfdf5;
  color: #047857;
}

.shopping-page .btn-complete {
  background-color: #ecfdf5;
  color: #047857;
}

.shopping-page .btn-complete:hover:not(:disabled) {
  background-color: #d1fae5;
}

/* 작은 화면에서는 검색 조건을 한 줄에 하나씩 배치한다. */
@media (max-width: 560px) {
  .shopping-search {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    width: 100%;
    margin-left: 0;
  }

  .shopping-search .search-field {
    display: grid;
    grid-template-columns: 80px minmax(0, 1fr);
  }

  .shopping-search input,
  .shopping-search .status-field select {
    width: 100%;
  }

  .shopping-search .btn {
    width: 100%;
  }
}
</style>
