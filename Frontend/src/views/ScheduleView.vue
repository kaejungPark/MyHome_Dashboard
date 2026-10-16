<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { CalendarDays, Plus, Pencil, Trash2, List } from '@lucide/vue' // 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'
import type { PageResponse } from '../types/page'
import { paginateDemo } from '../mocks/paginateDemo'
import Pagination from '../components/Pagination.vue'
import { usePagination } from '../composables/usePagination'
import { useErrorMessage } from '../composables/useErrorMessage'

// 일정 목록 조회와 수정 폼에서 사용하는 데이터다.
interface Schedule {
  id: number // 일정 ID
  title: string // 일정 제목
  scheduleType: 'GENERAL' | 'PAYMENT' | 'RENEWAL' // 일정 유형
  startDate: string // 시작일
  dueDate: string // 마감일
  amount: number | null // 관련 금액: 없으면 null
  completed: boolean // 완료 여부
  memo: string | null // 메모: 없으면 null
}

// 조회 결과와 화면 처리 상태를 관리한다.
const schedules = ref<Schedule[]>([])
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const notice = ref('') // 처리 완료 메시지
const editingId = ref<number | null>(null) // 수정 대상 ID: null이면 신규 등록
const isDemo = ref(false) // 조회 결과에 체험 데이터가 포함되었는지 관리, 실제 데이터 접근 권한은 서버에서도 검증
const { canModify } = useDemoGuard(isDemo) // canModify는 체험 상태에서 등록·수정 저장·삭제 요청을 진행하지 않도록 안내
const { errorMessage, showError } = useErrorMessage() // 화면의 오류 메시지와 공통 오류 처리 함수를 사용한다.

/**
 * 등록과 수정에서 함께 사용하는 반응형 입력 객체
 * 초기화할 때는 객체를 교체하지 않고 Object.assign으로 속성값을 갱신
 */
const form = reactive(initialForm())

// 첫 화면에서는 사용자 PC의 현재 월을 조회한다.
const now = new Date()
const month = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)

// 조회 버튼으로 확정한 월이다.
// 페이지 이동과 저장·삭제 후 재조회에도 같은 월을 사용한다.
const appliedMonth = ref(month.value)

// 일정 목록의 페이지 상태와 공통 처리 함수를 사용
const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(5)

/**
 * 브라우저의 로컬 날짜를 YYYY-MM-DD 형식으로 반환한다.
 * 신규 일정의 기본 시작일과 마감일로 사용한다.
 * UTC 문자열로 변환하지 않아 시간대 변환으로 날짜가 달라지는 것을 피한다.
 */
function today(): string {
  const date = new Date()

  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

/**
 * 신규 등록과 수정 취소에 사용할 기본 입력값을 생성
 */
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

/**
 * 검색창의 입력값을 실제 조회 조건으로 확정하고 첫 페이지를 조회
 * 수정 중이라면 입력 취소 여부를 먼저 확인
 * 검색 결과의 페이지 수가 달라질 수 있으므로 기존 페이지 번호는 초기화
 */
async function searchSchedules() {
  if (loading.value || saving.value) return

  const selectedMonth = month.value

  if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(selectedMonth)) {
    errorMessage.value = '조회 월을 선택해 주세요.'
    return
  }

  const year = Number(selectedMonth.slice(0, 4))

  if (year < 1 || year > 9998) {
    errorMessage.value = '지원하지 않는 연도입니다.'
    return
  }

  if (editingId.value !== null && !window.confirm('수정을 취소하고 검색할까요?')) {
    return
  }

  if (editingId.value !== null) resetForm()

  appliedMonth.value = selectedMonth
  resetPage()
  notice.value = ''

  await loadData()
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
 * 필수 입력과 금액을 검증한 뒤 일정을 등록하거나 수정
 * editingId가 없으면 POST로 등록하고, 있으면 해당 ID에 PUT으로 수정
 * 저장 성공 후 입력 폼을 초기화하고 적용된 검색 조건으로 목록을 다시 조회
 * 저장 요청이 실패하면 입력값을 유지
 */
async function saveSchedule() {
  if (saving.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  // 금액은 선택 입력 항목이므로, 입력값이 비어있다면 null로 처리하고 값이 있다면 숫자로 변환합니다.
  const amountText = String(form.amount).trim()
  const amount = amountText === '' ? null : Number(amountText)

  // 제목·유형·날짜를 필수 확인하고, 입력한 금액은 0 이상의 정수만 허용
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

  // 날짜 입력값은 YYYY-MM-DD 형식이므로 문자열로 순서를 비교
  if (form.dueDate < form.startDate) {
    errorMessage.value = '마감일은 시작일보다 빠를 수 없습니다.'
    return
  }

  // 요청 시작 시 수정 대상 ID를 보관
  // 저장 후 폼을 초기화해도 등록·수정 여부에 맞는 완료 문구를 표시할 수 있다.
  const id = editingId.value
  saving.value = true

  try {
    // 신규 등록은 POST, 수정은 ID를 포함한 PUT 요청으로 전송한다.
    await request<null>(id === null ? '/api/schedules' : `/api/schedules/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      // 서버 요청 타입에 맞게 숫자를 변환하고 문자열의 앞뒤 공백을 제거, 선택 입력인 관련 금액과 메모는 비어 있으면 null로 전달
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

    resetForm()
    notice.value = id === null ? '등록됐습니다.' : '수정됐습니다.'
    await loadData() // 저장된 내용과 검색 결과의 건수·페이지 변화를 반영하기 위해 재조회
  } catch (error: unknown) {
    // 저장 실패 시 입력 내용을 유지한다.
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
 * 선택한 일정을 수정 대상으로 지정하고 기존 값을 입력 폼에 복사
 * 폼에서 사용하는 타입에 맞춰 문자열로 변환
 */
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

/**
 * 사용자 확인 후 선택한 일정을 삭제
 * 조회·저장 중에는 중복 요청을 막고, 체험 상태에서는 삭제를 진행하지 않는다.
 * 삭제 성공 후 수정 대상과 목록을 갱신
 * 삭제로 현재 페이지가 사라지는 경우는 loadData에서 보정
 */
async function deleteSchedule(schedule: Schedule) {
  if (saving.value || loading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${schedule.title}" 일정을 삭제하시겠습니까?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`/api/schedules/${schedule.id}`, {
      method: 'DELETE',
    })

    // 수정 중이던 일정을 삭제했다면 폼도 초기화해 삭제된 항목을 다시 저장하지 않도록 한다.
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

/**
 * 선택한 월과 기간이 겹치는 일정을 페이지 단위로 조회
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
  schedules.value = []
  resetTotals()

  try {
    // 이번 조회에 사용할 확정된 월과 페이지 크기를 보관한다.
    const selectedMonth = appliedMonth.value
    const pageSize = size.value

    // 조회 월 검증
    if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(selectedMonth)) {
      throw new Error('조회 월을 선택해 주세요.')
    }

    const year = Number(selectedMonth.slice(0, 4))

    if (year < 1 || year > 9998) {
      throw new Error('지원하지 않는 연도입니다.')
    }

    // 같은 조회 월로 최초 조회와 마지막 페이지 보정에 사용한다.
    function fetchSchedulePage(targetPage: number) {
      return requestWithDemo<PageResponse<Schedule>>(
        `/api/schedules/${selectedMonth}?page=${targetPage}&size=${pageSize}`,
        () => {
          const demo = createDemoData(selectedMonth) // 조회에서 사용할 체험 데이터를 생성, 실제 API 대신 체험 데이터를 반환할 때 아래 콜백에서 사용

          // 조회 월과 기간이 겹치는 일정을 추리고 표시 순서를 정한다.
          const filtered = demo.schedules
            .filter(
              (item) =>
                item.startDate.slice(0, 7) <= selectedMonth &&
                item.dueDate.slice(0, 7) >= selectedMonth,
            )
            .sort(
              (a, b) =>
                Number(a.completed) - Number(b.completed) ||
                a.dueDate.localeCompare(b.dueDate) ||
                a.id - b.id,
            )
          // 필터링과 정렬을 마친 전체 결과에서 요청한 페이지의 항목만 추출, 전체 건수와 페이지 수도 필터링된 결과를 기준으로 계산
          return paginateDemo(filtered, targetPage, pageSize)
        },
      )
    }

    let result = await fetchSchedulePage(page.value)

    // 삭제나 수정으로 현재 페이지가 사라지면 마지막 유효 페이지를 조회한다.
    // 결과가 없어도 요청 페이지 번호는 최소 1로 유지한다.
    const lastPage = Math.max(1, result.data.totalPages)

    if (page.value > lastPage) {
      page.value = lastPage
      result = await fetchSchedulePage(lastPage)
    }

    schedules.value = result.data.items
    applyPage(result.data)
    isDemo.value = result.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false // 성공 여부와 관계없이 로딩을 해제해 다시 조회
  }
}

// 화면이 처음 표시되면 일정 목록을 조회한다.
onMounted(loadData)
</script>
<template>
  <section class="crud-page schedule-page">
    <header class="page-heading">
      <div class="page-icon">
        <CalendarDays :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>일정 관리</h1>
        <p>일정을 등록하고 마감일과 완료 여부를 확인합니다.</p>
      </div>
    </header>

    <p v-if="isDemo" class="demo-notice" role="status">
      체험 중이에요. 현재 정보는 샘플 데이터입니다. 로그인하면 내 정보를 관리할 수 있어요.
    </p>

    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" class="message success-message" role="status">{{ notice }}</p>

    <form class="card" @submit.prevent="saveSchedule">
      <div class="card-heading">
        <div class="heading-title">
          <component :is="editingId === null ? Plus : Pencil" :size="20" aria-hidden="true" />
          <h2>{{ editingId === null ? '일정 등록' : '일정 수정' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving || loading">
        <legend class="sr-only">일정 관리 입력</legend>
        <div class="form-grid">
          <label>
            제목 *
            <input v-model="form.title" required maxlength="200" />
          </label>

          <label>
            일정 유형 *
            <select v-model="form.scheduleType" required>
              <option value="GENERAL">일반 일정</option>
              <option value="PAYMENT">납부</option>
              <option value="RENEWAL">갱신</option>
            </select>
          </label>

          <label>
            시작일 *
            <input v-model="form.startDate" type="date" required />
          </label>

          <label>
            마감일 *
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

    <section class="card" aria-labelledby="schedule-list-heading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2 id="schedule-list-heading">일정 목록</h2>
          <span v-if="!loading" class="count-badge"> {{ totalCount }}건</span>
        </div>

        <!-- 조회 월을 선택하고 해당 월의 일정을 검색한다. -->
        <form class="month-search" @submit.prevent="searchSchedules">
          <label class="month-field">
            조회 월
            <input
              v-model="month"
              type="month"
              min="0001-01"
              max="9998-12"
              :disabled="loading || saving"
              required
            />
          </label>

          <button type="submit" class="btn btn-primary" :disabled="loading || saving">
            {{ loading ? '조회 중...' : '조회' }}
          </button>
        </form>
      </div>

      <p v-if="loading" class="empty-state" role="status">조회 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="schedules.length === 0" class="empty-state">등록된 일정이 없습니다.</p>

      <div v-if="!loading && schedules.length > 0" class="table-wrap">
        <table class="data-table schedule-table">
          <caption class="sr-only">
            등록된 일정
          </caption>
          <thead>
            <tr>
              <th scope="col">제목</th>
              <th scope="col">유형</th>
              <th scope="col">시작일</th>
              <th scope="col">마감일</th>
              <th scope="col" class="amount-cell">금액</th>
              <th scope="col">상태</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="schedule in schedules"
              :key="schedule.id"
              :class="{ 'editing-row': editingId === schedule.id }"
            >
              <td class="title-cell">{{ schedule.title }}</td>
              <td>
                <span class="badge">{{ scheduleTypeLabel(schedule.scheduleType) }}</span>
              </td>
              <td class="date-cell">{{ schedule.startDate }}</td>
              <td class="date-cell">{{ schedule.dueDate }}</td>
              <td class="amount-cell">
                {{
                  schedule.amount === null ? '-' : `${schedule.amount.toLocaleString('ko-KR')}원`
                }}
              </td>
              <td>
                <span class="badge" :class="{ 'status-incomplete': schedule.completed === false }">
                  {{ schedule.completed ? '완료' : '미완료' }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="saving || loading"
                    :aria-label="`${schedule.title} 수정`"
                    @click="startEdit(schedule)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>
                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="saving || loading"
                    :aria-label="`${schedule.title} 삭제`"
                    @click="deleteSchedule(schedule)"
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
.schedule-table {
  min-width: 740px;
}

.schedule-table .title-cell {
  min-width: 150px;
  max-width: 280px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.schedule-page .badge.status-incomplete {
  background-color: #eff6ff;
  color: #b91c1c;
}

.demo-notice {
  margin: 0 0 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #eaf1ff;
  color: #2563eb;
  font-size: 14px;
}

/* 월 선택과 조회 버튼을 한 줄에 배치한다. */
.month-search {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.month-field {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.month-field input {
  width: 240px;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background-color: #fff;
  color: #1e293b;
  font: inherit;
}

.month-field input:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.month-search .btn {
  min-height: 44px;
}

@media (max-width: 640px) {
  .month-field {
    flex: 1;
    min-width: 0;
  }

  .month-field input {
    width: 100%;
  }
}
</style>
