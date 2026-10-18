<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { Carrot, Plus, Pencil, Trash2, List } from '@lucide/vue' // 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'
import type { PageResponse } from '../types/page'
import { paginateDemo } from '../mocks/paginateDemo'
import Pagination from '../components/Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { useErrorMessage } from '../composables/useErrorMessage'
import { today } from '../utils/date'

type IngredientStorageLocation = 'REFRIGERATOR' | 'FREEZER' | 'PANTRY' // 식재료 보관 장소 상태값
type IngredientStatus = 'STORED' | 'CONSUMED' | 'DISPOSED' // 식재료 상태값
type SearchLocation = '' | 'REFRIGERATOR' | 'FREEZER' | 'PANTRY' // 식재료 보관 장소 조회
type SearchStatus = '' | 'STORED' | 'CONSUMED' | 'DISPOSED' // 식재료 상태값 조회

// 식재료 목록 조회와 수정 폼에서 사용하는 데이터다.
interface Ingredient {
  id: number // 식재료 ID
  name: string // 식재료명
  quantity: number // 수량. 소수점
  unit: string // 단위
  storageLocation: IngredientStorageLocation // 보관 장소
  purchaseDate: string | null // 구매일
  expirationDate: string | null // 소비기한
  status: IngredientStatus // 관리 상태
  memo: string | null // 메모
}

const Ingredients = ref<Ingredient[]>([]) // 조회한 식재료 목록
const searchKeyword = ref('') // 검색창에 입력 식재료명
const searchLocations = ref<SearchLocation>('') // 검색창에 선택되는 보관장소
const searchStatus = ref<SearchStatus>('') // 검색창에 선택되는 상태
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const notice = ref('') // 처리 완료 메시지
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록
const { errorMessage, showError } = useErrorMessage() // 화면의 오류 메시지와 공통 오류 처리 함수를 사용한다.

const baseUrl = '/api/ingredients'

// 식재료 목록의 페이지 상태와 공통 처리 함수를 사용
const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(5)

/**
 * 마지막으로 검색 버튼을 눌렀을 때의 조건을 저장
 * 페이지 이동이나 저장·삭제 후 재조회할 때도 이 조건을 사용
 * 검색창을 수정만 하고 조회하지 않은 값이 페이지 이동에 반영되는 것을 방지
 */
const appliedFilters = reactive({
  keyword: '',
  storageLocation: '' as SearchLocation,
  status: '' as SearchStatus,
})

/**
 * 등록과 수정에서 함께 사용하는 반응형 입력 객체
 * 초기화할 때는 객체를 교체하지 않고 Object.assign으로 속성값을 갱신
 */
const form = reactive(initialForm())

const isDemo = ref(false) // 조회 결과에 체험 데이터가 포함되었는지 관리, 실제 데이터 접근 권한은 서버에서도 검증
const { canModify } = useDemoGuard(isDemo) // canModify는 체험 상태에서 등록·수정 저장·삭제 요청을 진행하지 않도록 안내

// 한국 기준 오늘을 날짜 차이 계산에 사용할 UTC 자정 값으로 변환한다.
const getToday = (): number => {
  return new Date(`${today()}T00:00:00Z`).getTime()
}

// 선택 입력은 빈 문자열로 초기화하고, 저장할 때 null로 변환한다.
function initialForm() {
  return {
    name: '',
    quantity: 0,
    unit: '',
    storageLocation: 'REFRIGERATOR' as IngredientStorageLocation,
    purchaseDate: '',
    expirationDate: '',
    status: 'STORED' as IngredientStatus,
    memo: '',
  }
}

// 보관 장소 코드를 화면에 표시할 한글 명칭으로 변환한다.
function storageLocationLabel(location: IngredientStorageLocation): string {
  switch (location) {
    case 'REFRIGERATOR':
      return '냉장'
    case 'FREEZER':
      return '냉동'
    case 'PANTRY':
      return '실온'
    default:
      return location
  }
}

// 식재료 상태 코드를 화면에 표시할 한글 명칭으로 변환한다.
function statusLabel(status: IngredientStatus): string {
  switch (status) {
    case 'STORED':
      return '보관 중'
    case 'CONSUMED':
      return '소비 완료'
    case 'DISPOSED':
      return '폐기'
    default:
      return status
  }
}

// 소비기한과 한국 기준 오늘을 모두 UTC 자정 값으로 비교해 남은 일수를 계산한다.
const calculateRemainingDays = (expiryDateString: string | null): number | null => {
  if (!expiryDateString) return null

  const expiry = new Date(`${expiryDateString}T00:00:00Z`).getTime()
  if (!Number.isFinite(expiry)) return null

  return Math.round((expiry - getToday()) / (1000 * 60 * 60 * 24))
}

// 남은 일수에 따라 문구와 스타일 클래스를 결정
const getExpiryStatus = (status: IngredientStatus, expiryDateString: string | null) => {
  // 보관 중(STORED)이 아니거나(소비 완료·폐기) 소비기한이 없는 경우
  if (status !== 'STORED' || !expiryDateString) {
    return { text: '', className: 'text-gray-400' }
  }

  const diffDays = calculateRemainingDays(expiryDateString)

  if (diffDays === null) {
    return { text: '', className: 'text-gray-400' }
  } else if (diffDays < 0) {
    return { text: '기한 경과', className: 'text-red-500 font-semibold' }
  } else if (diffDays === 0) {
    return { text: '오늘까지', className: 'text-red-500 font-semibold' }
  } else if (diffDays >= 1 && diffDays <= 3) {
    return { text: `${diffDays}일 남음 · 임박`, className: 'text-orange-500 font-semibold' }
  } else {
    return { text: `${diffDays}일 남음`, className: 'text-gray-600' }
  }
}

// 다시 조회할 때는 첫 페이지부터 표시한다.
async function searchIngredients() {
  if (loading.value || saving.value) return

  if (editingId.value !== null && !window.confirm('수정을 취소하고 검색할까요?')) {
    return
  }

  if (editingId.value !== null) resetForm()

  // 식재료명의 앞뒤 공백을 제거하고, 현재 입력값을 적용된 검색 조건에 복사
  Object.assign(appliedFilters, {
    keyword: searchKeyword.value.trim(),
    storageLocation: searchLocations.value,
    status: searchStatus.value,
  })

  resetPage() // 새로운 검색은 첫 페이지부터 시작하고 이전 처리 완료 안내
  notice.value = ''
  await loadData() // 확정한 검색 조건으로 식재료 목록을 조회한다.
}

// 선택한 페이지의 식재료 목록을 다시 조회한다.
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
 * 선택한 식재료를 수정 대상으로 지정하고 기존 값을 입력 폼에 복사
 */
function startEdit(item: Ingredient) {
  editingId.value = item.id
  errorMessage.value = ''
  notice.value = ''

  // 선택 입력의 null은 입력창에서 사용할 빈 문자열로 변환한다.
  Object.assign(form, {
    name: item.name,
    quantity: item.quantity,
    unit: item.unit,
    storageLocation: item.storageLocation,
    purchaseDate: item.purchaseDate ?? '',
    expirationDate: item.expirationDate ?? '',
    status: item.status,
    memo: item.memo ?? '',
  })

  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/**
 * 필수 입력과 수량·보관 장소·상태를 검증한 뒤 식재료를 등록하거나 수정
 * editingId가 없으면 POST로 등록하고, 있으면 해당 ID에 PUT으로 수정
 * 저장 성공 후 입력 폼을 초기화하고 적용된 검색 조건으로 목록을 다시 조회
 * 저장 요청이 실패하면 입력값을 유지
 */
async function saveIngredient() {
  if (saving.value || loading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  const name = form.name.trim()
  const unit = form.unit.trim()
  const memo = form.memo.trim()
  const quantityText = String(form.quantity).trim()
  const quantity = Number(quantityText)

  if (!name || !unit) {
    errorMessage.value = '식재료명과 단위를 입력해 주세요.'
    return
  }

  if (name.length > 100 || unit.length > 20 || memo.length > 500) {
    errorMessage.value = '식재료명은 100자, 단위는 20자, 메모는 500자까지 입력할 수 있습니다.'
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

  if (!['REFRIGERATOR', 'FREEZER', 'PANTRY'].includes(form.storageLocation)) {
    errorMessage.value = '보관 장소를 선택해 주세요.'
    return
  }

  if (!['STORED', 'CONSUMED', 'DISPOSED'].includes(form.status)) {
    errorMessage.value = '올바른 상태를 선택해 주세요.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    await request<null>(id === null ? baseUrl : `${baseUrl}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      // 서버 요청 타입에 맞게 숫자를 변환하고 문자열의 앞뒤 공백을 제거, 선택 입력인 구매일·소비기한·메모는 비어 있으면 null로 전달
      body: JSON.stringify({
        name,
        quantity,
        unit,
        storageLocation: form.storageLocation,
        purchaseDate: form.purchaseDate || null,
        expirationDate: form.expirationDate || null,
        status: form.status,
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
 * 사용자 확인 후 선택한 식재료를 삭제
 * 조회·저장 중에는 중복 요청을 막고, 체험 상태에서는 삭제를 진행하지 않는다.
 * 삭제 성공 후 수정 대상과 목록을 갱신
 * 삭제로 현재 페이지가 사라지는 경우는 loadData에서 보정
 */
async function deleteIngredient(item: Ingredient) {
  if (saving.value || loading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${item.name}" 식재료를 삭제할까요?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`${baseUrl}/${item.id}`, { method: 'DELETE' })

    if (editingId.value === item.id) resetForm()

    notice.value = '식재료가 삭제됐습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 검색 조건에 맞는 식재료 목록을 조회
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
  Ingredients.value = []
  resetTotals()

  try {
    const filters = { ...appliedFilters } // 이번 조회에 사용할 조건과 페이지 크기를 복사, 마지막 페이지 보정으로 재요청하더라도 같은 조건을 사용
    const pageSize = size.value

    const demo = createDemoData() // 조회에서 사용할 체험 데이터를 생성, 실제 API 대신 체험 데이터를 반환할 때 아래 콜백에서 사용

    // 지정한 페이지를 조회하는 내부 함수, 최초 조회와 마지막 페이지 보정에 같은 요청 구성을 재사용
    const fetchIngredientPage = (targetPage: number) => {
      const params = new URLSearchParams({
        keyword: filters.keyword,
        storageLocation: filters.storageLocation,
        status: filters.status,
        page: String(targetPage),
        size: String(pageSize),
      })

      return requestWithDemo<PageResponse<Ingredient>>(`${baseUrl}?${params.toString()}`, () => {
        /**
         * 체험 목록에도 식재료명·보관장소·상태 유형 조건을 적용
         * 식재료명은 대소문자를 구분하지 않는 포함 검색이며, 빈 조건은 전체로 처리
         */
        const filtered = demo.ingredients.filter(
          (ingredients) =>
            (!filters.keyword ||
              ingredients.name.toLowerCase().includes(filters.keyword.toLowerCase())) &&
            (!filters.storageLocation || ingredients.storageLocation === filters.storageLocation) &&
            (!filters.status || ingredients.status === filters.status),
        )

        // 필터링한 전체 결과에서 요청한 페이지의 항목만 추출, 전체 건수와 페이지 수도 필터링된 결과를 기준으로 계산
        return paginateDemo(filtered, targetPage, pageSize)
      })
    }

    let result = await fetchIngredientPage(page.value)

    const lastPage = Math.max(1, result.data.totalPages)

    if (page.value > lastPage) {
      page.value = lastPage
      result = await fetchIngredientPage(lastPage)
    }

    Ingredients.value = result.data.items
    applyPage(result.data)
    isDemo.value = result.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 화면이 처음 표시되면 식재료 목록을 조회한다.
onMounted(loadData)
</script>
<template>
  <section class="crud-page ingredient-page">
    <header class="page-heading">
      <div class="page-icon">
        <Carrot :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>식재료 관리</h1>
        <p>식재료의 보관 장소와 소비기한을 확인하고 관리합니다.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험 중이에요. 현재 정보는 샘플 데이터입니다. 로그인하면 내 식재료를 관리할 수 있어요.
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <!-- 등록과 수정에 같은 폼을 사용한다. -->
    <form class="card" @submit.prevent="saveIngredient">
      <div class="card-heading">
        <div class="heading-title">
          <component :is="editingId === null ? Plus : Pencil" :size="20" aria-hidden="true" />
          <h2>{{ editingId === null ? '식재료 등록' : '식재료 수정' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving || loading">
        <legend class="sr-only">식재료 정보 입력</legend>

        <div class="form-grid">
          <label>
            식재료명 *
            <input v-model="form.name" maxlength="100" placeholder="예: 우유, 계란" required />
          </label>

          <label>
            보관 장소 *
            <select v-model="form.storageLocation" required>
              <option value="REFRIGERATOR">냉장</option>
              <option value="FREEZER">냉동</option>
              <option value="PANTRY">실온</option>
            </select>
          </label>

          <label>
            수량 *
            <input
              v-model.number="form.quantity"
              type="number"
              inputmode="decimal"
              min="0.01"
              max="99999999.99"
              step="0.01"
              required
            />
          </label>

          <label>
            단위 *
            <input v-model="form.unit" maxlength="20" placeholder="예: 개, 팩, kg" required />
          </label>

          <label>
            구매일
            <input v-model="form.purchaseDate" type="date" />
          </label>

          <label>
            소비기한
            <input v-model="form.expirationDate" type="date" />
          </label>

          <label>
            상태 *
            <select v-model="form.status" required>
              <option value="STORED">보관 중</option>
              <option value="CONSUMED">소비 완료</option>
              <option value="DISPOSED">폐기</option>
            </select>
          </label>

          <label class="memo-field">
            메모
            <textarea
              v-model="form.memo"
              maxlength="500"
              rows="3"
              placeholder="보관 방법이나 참고할 내용을 입력해 주세요."
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
            <component :is="editingId === null ? Plus : Pencil" :size="16" aria-hidden="true" />
            {{ saving ? '저장 중…' : editingId === null ? '등록' : '수정 저장' }}
          </button>
        </div>
      </fieldset>
    </form>

    <!-- 상태와 보관 장소를 함께 표시하는 식재료 목록 -->
    <section class="card" aria-labelledby="ingredient-list-heading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2 id="ingredient-list-heading">식재료 목록</h2>
          <span v-if="!loading" class="count-badge"> {{ totalCount }}건 </span>
        </div>
        <!-- 식재료명·보관 장소·상태를 함께 적용해 검색한다. -->
        <form class="ingredient-search" @submit.prevent="searchIngredients">
          <label class="search-field">
            식재료명
            <input
              v-model="searchKeyword"
              type="search"
              placeholder="식재료명 입력"
              :disabled="loading || saving"
            />
          </label>

          <label class="search-field status-field">
            보관장소
            <select v-model="searchLocations" :disabled="loading || saving">
              <option value="">전체</option>
              <option value="REFRIGERATOR">냉장</option>
              <option value="FREEZER">냉동</option>
              <option value="PANTRY">실온</option>
            </select>
          </label>

          <label class="search-field status-field">
            상태
            <select v-model="searchStatus" :disabled="loading || saving">
              <option value="">전체</option>
              <option value="STORED">보관 중</option>
              <option value="CONSUMED">소비 완료</option>
              <option value="DISPOSED">폐기</option>
            </select>
          </label>

          <button type="submit" class="btn btn-primary" :disabled="loading || saving">
            {{ loading ? '조회 중...' : '검색' }}
          </button>
        </form>
      </div>

      <p v-if="loading" class="empty-state" role="status">식재료를 불러오는 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="Ingredients.length === 0" class="empty-state">조회된 식재료가 없습니다.</p>

      <div v-if="!loading && Ingredients.length > 0" class="table-wrap">
        <table class="data-table ingredient-table">
          <caption class="sr-only">
            등록된 식재료의 수량, 보관 장소, 소비기한과 상태
          </caption>
          <thead>
            <tr>
              <th scope="col">식재료명</th>
              <th scope="col" class="amount-cell">수량</th>
              <th scope="col">보관 장소</th>
              <th scope="col">구매일</th>
              <th scope="col">소비기한</th>
              <th scope="col">상태</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="item in Ingredients"
              :key="item.id"
              :class="{ 'editing-row': editingId === item.id }"
            >
              <td class="ingredient-name-cell">
                <span class="ingredient-name">{{ item.name }}</span>
                <span v-if="item.memo" class="ingredient-memo">
                  {{ item.memo }}
                </span>
              </td>

              <td class="amount-cell">
                {{
                  item.quantity.toLocaleString('ko-KR', {
                    maximumFractionDigits: 2,
                  })
                }}
                {{ item.unit }}
              </td>

              <td>{{ storageLocationLabel(item.storageLocation) }}</td>
              <td class="date-cell">{{ item.purchaseDate ?? '미입력' }}</td>
              <!-- 소비기한 셀 수정 -->
              <td class="date-cell">
                <div>{{ item.expirationDate ?? '미입력' }}</div>
                <!-- 보관 중(STORED)일 때만 소비기한 아래에 남은 기간 표시 -->
                <div
                  v-if="item.status === 'STORED' && item.expirationDate"
                  class="text-xs expiry-notice"
                >
                  <span :class="getExpiryStatus(item.status, item.expirationDate).className">
                    {{ getExpiryStatus(item.status, item.expirationDate).text }}
                  </span>
                </div>
              </td>

              <td>
                <span
                  class="badge"
                  :class="{
                    'ingredient-stored': item.status === 'STORED',
                    'ingredient-consumed': item.status === 'CONSUMED',
                    'ingredient-disposed': item.status === 'DISPOSED',
                  }"
                >
                  {{ statusLabel(item.status) }}
                </span>
              </td>

              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="saving || loading"
                    :aria-label="`${item.name} 수정`"
                    @click="startEdit(item)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>

                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="saving || loading"
                    :aria-label="`${item.name} 삭제`"
                    @click="deleteIngredient(item)"
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
/* 작은 화면에서는 표 영역 안에서 가로로 스크롤한다. */
.ingredient-table {
  min-width: 850px;
}

.ingredient-table .ingredient-name-cell {
  min-width: 150px;
  max-width: 280px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.ingredient-name {
  display: block;
  font-weight: 600;
}

.ingredient-memo {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

/* 색상과 한글 명칭으로 식재료 상태를 구분한다. */
.ingredient-page .badge.ingredient-stored {
  color: #1d4ed8;
  background: #eff6ff;
}

.ingredient-page .badge.ingredient-consumed {
  color: #166534;
  background: #f0fdf4;
}

.ingredient-page .badge.ingredient-disposed {
  color: #475569;
  background: #f1f5f9;
}

.demo-notice {
  margin: 0 0 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #eaf1ff;
  color: #2563eb;
  font-size: 14px;
  line-height: 1.6;
}

/* 기존 스타일 외에 추가하거나 프로젝트에 맞게 정의할 수 있는 클래스입니다 */
.text-xs {
  font-size: 0.75rem;
  line-height: 1rem;
}

.text-red-500 {
  color: #ef4444;
}
.text-orange-500 {
  color: #f97316;
}
.text-gray-400 {
  color: #9ca3af;
}
.text-gray-600 {
  color: #4b5563;
}
.font-semibold {
  font-weight: 600;
}

/* 소비기한 날짜와 남은 기간 안내 사이에 간격을 둔다. */
.expiry-notice {
  margin-top: 2px;
}

/* 검색 필터와 버튼을 목록 상단 오른쪽에 배치한다. */
.ingredient-search {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.ingredient-search .search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.ingredient-search input,
.ingredient-search select {
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

.ingredient-search .status-field select {
  width: 120px;
}

.ingredient-search input:focus-visible,
.ingredient-search select:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.ingredient-search input:disabled,
.ingredient-search select:disabled {
  background-color: #f8fafc;
  cursor: not-allowed;
}

.ingredient-search .btn {
  min-height: 44px;
}

@media (max-width: 560px) {
  .ingredient-page .card-heading {
    flex-wrap: wrap;
    gap: 12px;
  }
  .ingredient-search {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .ingredient-search .search-field {
    display: grid;
    grid-template-columns: 52px minmax(0, 1fr);
  }

  .ingredient-search input,
  .ingredient-search .status-field select {
    width: 100%;
  }

  .ingredient-search .btn {
    width: 100%;
  }
}
</style>
