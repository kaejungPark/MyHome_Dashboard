<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { ListChecks, Plus, Pencil, Trash2, List } from '@lucide/vue' // 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'
import type { PageResponse } from '../types/page'
import { paginateDemo } from '../mocks/paginateDemo'
import Pagination from '../components/Pagination.vue'

type TaskType = 'CHORE' | 'CONSUMABLE'

interface HouseholdTask {
  id: number
  name: string
  taskType: TaskType
  cycleDays: number
  nextDueDate: string
  active: boolean
  memo: string | null
}

interface HouseholdTaskHistory {
  id: number
  taskId: number
  completedDate: string
  memo: string | null
  createdDt: string
}

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)
const { canModify } = useDemoGuard(isDemo)

// 폼 입력값을 화면과 동기화한다.
const form = reactive(initialForm())

const completion = reactive(completionForm())
const completingTask = ref<HouseholdTask | null>(null) // 완료 처리할 항목
const historyTask = ref<HouseholdTask | null>(null) // 이력을 조회하는 항목
const historyLoading = ref(false) // 완료 이력 조회 중 여부
const HouseholdTasks = ref<HouseholdTask[]>([])
const HouseholdTaskHistorys = ref<HouseholdTaskHistory[]>([])
// 관리 목록 페이징
const page = ref(1)
const size = ref(5)
const totalCount = ref(0)
const totalPages = ref(0)
// 관리 목록 검색 조건
const searchName = ref('')
const searchTaskType = ref<'' | TaskType>('')
const searchActive = ref<'' | boolean>('')
// 선택한 항목의 완료 이력 페이징
const historyPage = ref(1)
const historySize = ref(5)
const historyTotalCount = ref(0)
const historyTotalPages = ref(0)
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const editingId = ref<number | null>(null) // null이면 신규 등록
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 완료 메시지

const baseUrl = '/api/household-tasks'

// 검색 버튼으로 적용한 조건을 페이지 이동에도 유지한다.
const appliedFilters = reactive({
  name: '',
  taskType: '' as '' | TaskType,
  active: '' as '' | boolean,
})

function initialForm() {
  return {
    name: '',
    taskType: 'CHORE' as TaskType,
    cycleDays: 0,
    nextDueDate: '',
    active: true,
    memo: '',
  }
}

// 완료 처리에 필요한 입력값을 초기화한다.
function completionForm() {
  return {
    completedDate: todayText(),
    memo: '',
  }
}

// 한국 시간을 기준으로 오늘 날짜를 반환한다.
function todayText(): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

// 오류를 개발자 도구에 기록하고 화면에 안내 메시지를 표시한다.
function showError(error: unknown) {
  console.error(error)
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 입력값과 수정 대상을 초기화한다.
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

// 서버의 관리 유형을 화면에 표시할 명칭으로 변환한다.
function taskTypeLabel(taskType: TaskType): string {
  return taskType === 'CHORE' ? '집안일' : '소모품'
}

// 입력값을 검증한 뒤 신규 등록 또는 수정을 요청한다.
async function saveTask() {
  if (loading.value || saving.value || historyLoading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  const name = form.name.trim()
  const memo = form.memo.trim()

  // 등록 수정 필수 validation 검증
  if (!name || name.length > 100) {
    errorMessage.value = '항목명은 1~100자로 입력해 주세요.'
    return
  }

  if (!Number.isInteger(form.cycleDays) || form.cycleDays < 1 || form.cycleDays > 2147483647) {
    errorMessage.value = '반복 주기는 1 이상의 정수로 입력해 주세요.'
    return
  }

  if (!form.nextDueDate) {
    errorMessage.value = '다음 예정일을 입력해 주세요.'
    return
  }

  if (memo.length > 500) {
    errorMessage.value = '메모는 500자까지 입력할 수 있습니다.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    await request<void>(id === null ? baseUrl : `${baseUrl}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name,
        taskType: form.taskType,
        cycleDays: form.cycleDays,
        nextDueDate: form.nextDueDate,
        active: form.active,
        memo: memo || null,
      }),
    })

    // 열려 있는 이력 영역에도 변경된 항목명을 반영한다.
    if (historyTask.value?.id === id) {
      historyTask.value = { ...historyTask.value, name }
    }

    resetForm()
    notice.value = id === null ? '등록했습니다.' : '수정했습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 선택한 항목의 정보를 수정 폼에 채운다.
function startEdit(task: HouseholdTask) {
  if (loading.value || saving.value || historyLoading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''
  completingTask.value = null
  editingId.value = task.id

  Object.assign(form, {
    name: task.name,
    taskType: task.taskType,
    cycleDays: task.cycleDays,
    nextDueDate: task.nextDueDate,
    active: task.active,
    memo: task.memo ?? '',
  })
}

// 삭제 가능 여부는 서버에서 완료 이력과 소유자를 기준으로 검사한다.
async function deleteTask(task: HouseholdTask) {
  if (loading.value || saving.value || historyLoading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${task.name}" 항목을 삭제할까요?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<void>(`${baseUrl}/${task.id}`, { method: 'DELETE' })

    if (editingId.value === task.id) resetForm()
    if (completingTask.value?.id === task.id) completingTask.value = null

    if (historyTask.value?.id === task.id) {
      historyTask.value = null
      HouseholdTaskHistorys.value = []

      historyPage.value = 1
      historyTotalCount.value = 0
      historyTotalPages.value = 0
    }

    notice.value = '삭제했습니다.'
    await loadData()
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 선택한 항목의 완료 입력 영역 오픈
function startComplete(task: HouseholdTask) {
  if (loading.value || saving.value || historyLoading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  if (!task.active) {
    errorMessage.value = '비활성 항목은 완료 처리할 수 없습니다.'
    return
  }

  resetForm()
  completingTask.value = task
  Object.assign(completion, completionForm())
}

// 완료 입력을 취소하고 초기화
function cancelComplete() {
  if (saving.value) return

  completingTask.value = null
  Object.assign(completion, completionForm())
}

// 선택한 항목의 완료 이력을 페이지 단위로 조회한다.
async function loadHistory(task: HouseholdTask) {
  if (historyLoading.value) return

  historyLoading.value = true
  historyTask.value = task
  HouseholdTaskHistorys.value = []
  historyTotalCount.value = 0
  historyTotalPages.value = 0
  errorMessage.value = ''

  try {
    async function fetchHistoryPage(
      targetPage: number,
    ): Promise<PageResponse<HouseholdTaskHistory>> {
      if (isDemo.value) {
        const histories = createDemoData()
          .householdTaskHistories.filter((history) => history.taskId === task.id)
          .sort((a, b) => b.completedDate.localeCompare(a.completedDate) || b.id - a.id)

        return paginateDemo(histories, targetPage, historySize.value)
      }

      const params = new URLSearchParams({
        page: String(targetPage),
        size: String(historySize.value),
      })

      const result = await request<PageResponse<HouseholdTaskHistory>>(
        `${baseUrl}/${task.id}/completions?${params.toString()}`,
      )

      if (!result) {
        throw new Error('완료 이력 조회 결과가 없습니다.')
      }

      return result
    }

    let result = await fetchHistoryPage(historyPage.value)
    const lastPage = Math.max(1, result.totalPages)

    if (historyPage.value > lastPage) {
      historyPage.value = lastPage
      result = await fetchHistoryPage(lastPage)
    }

    HouseholdTaskHistorys.value = result.items
    historyPage.value = result.page
    historyTotalCount.value = result.totalCount
    historyTotalPages.value = result.totalPages
  } catch (error: unknown) {
    showError(error)
  } finally {
    historyLoading.value = false
  }
}

// 다른 항목의 이력을 열 때는 첫 페이지부터 조회한다.
async function showHistory(task: HouseholdTask) {
  if (loading.value || saving.value || historyLoading.value) return

  historyPage.value = 1
  await loadHistory(task)
}

// 완료 이력 영역과 페이지 정보를 초기화한다.
function closeHistory() {
  if (loading.value || saving.value || historyLoading.value) return

  historyTask.value = null
  HouseholdTaskHistorys.value = []
  historyPage.value = 1
  historyTotalCount.value = 0
  historyTotalPages.value = 0
}

// 관리 목록 페이지와 독립적으로 완료 이력 페이지를 이동한다.
async function changeHistoryPage(targetPage: number) {
  if (loading.value || saving.value || historyLoading.value) return
  if (!historyTask.value) return
  if (targetPage < 1 || targetPage > historyTotalPages.value) return
  if (targetPage === historyPage.value) return

  historyPage.value = targetPage
  await loadHistory(historyTask.value)
}

// 완료일과 메모를 저장하고 다음 예정일과 열린 이력을 갱신한다.
async function completeTask() {
  if (loading.value || saving.value || historyLoading.value) return
  if (!canModify()) return

  const task = completingTask.value
  if (!task) return

  errorMessage.value = ''
  notice.value = ''

  if (!completion.completedDate || completion.completedDate > todayText()) {
    errorMessage.value = '완료일은 오늘 또는 과거 날짜로 입력해 주세요.'
    return
  }

  const memo = completion.memo.trim()

  if (memo.length > 500) {
    errorMessage.value = '메모는 500자까지 입력할 수 있습니다.'
    return
  }

  saving.value = true

  try {
    await request<void>(`${baseUrl}/${task.id}/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        completedDate: completion.completedDate,
        memo: memo || null,
      }),
    })

    completingTask.value = null
    Object.assign(completion, completionForm())
    notice.value = '완료 처리했습니다.'

    await loadData()

    // 완료 처리 후에는 이력 첫 페이지를 다시 조회한다.
    if (!errorMessage.value && historyTask.value?.id === task.id) {
      historyPage.value = 1
      await loadHistory(task)
    }
  } catch (error: unknown) {
    showError(error)
  } finally {
    saving.value = false
  }
}

// 목록을 변경하기 전에 진행 중인 수정·완료 입력을 정리한다.
function prepareListChange(): boolean {
  if (editingId.value !== null || completingTask.value !== null) {
    if (!window.confirm('작성 중인 내용을 취소하고 목록을 조회할까요?')) {
      return false
    }

    resetForm()
    cancelComplete()
  }

  closeHistory()
  notice.value = ''
  return true
}

// 입력한 검색 조건을 적용하고 첫 페이지를 조회한다.
async function searchTasks() {
  if (loading.value || saving.value || historyLoading.value) return
  if (!prepareListChange()) return

  Object.assign(appliedFilters, {
    name: searchName.value.trim(),
    taskType: searchTaskType.value,
    active: searchActive.value,
  })

  page.value = 1
  await loadData()
}

// 적용된 검색 조건을 유지하면서 관리 목록 페이지를 이동한다.
async function changePage(targetPage: number) {
  if (loading.value || saving.value || historyLoading.value) return
  if (targetPage < 1 || targetPage > totalPages.value) return
  if (targetPage === page.value) return
  if (!prepareListChange()) return

  page.value = targetPage
  await loadData()
}

// 검색 조건에 맞는 집안일·소모품 목록을 페이지 단위로 조회한다.
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  HouseholdTasks.value = []
  totalCount.value = 0
  totalPages.value = 0

  try {
    const demo = createDemoData()
    const { name, taskType, active } = appliedFilters

    // 실제 조회와 체험 조회에 동일한 검색 조건을 적용한다.
    function fetchPage(targetPage: number) {
      const params = new URLSearchParams({
        name,
        taskType,
        page: String(targetPage),
        size: String(size.value),
      })

      if (active !== '') {
        params.set('active', String(active))
      }

      return requestWithDemo<PageResponse<HouseholdTask>>(`${baseUrl}?${params.toString()}`, () => {
        const filtered = demo.householdTasks
          .filter(
            (task) =>
              task.name.toLowerCase().includes(name.toLowerCase()) &&
              (taskType === '' || task.taskType === taskType) &&
              (active === '' || task.active === active),
          )
          .sort(
            (a, b) =>
              Number(b.active) - Number(a.active) ||
              a.nextDueDate.localeCompare(b.nextDueDate) ||
              b.id - a.id,
          )

        return paginateDemo(filtered, targetPage, size.value)
      })
    }

    let result = await fetchPage(page.value)

    // 삭제 등으로 현재 페이지가 사라졌다면 마지막 페이지를 조회한다.
    const lastPage = Math.max(1, result.data.totalPages)

    if (page.value > lastPage) {
      page.value = lastPage
      result = await fetchPage(lastPage)
    }

    HouseholdTasks.value = result.data.items
    page.value = result.data.page
    totalCount.value = result.data.totalCount
    totalPages.value = result.data.totalPages
    isDemo.value = result.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 화면이 처음 표시되면 집안일·소모품 목록을 조회한다.
onMounted(loadData)
</script>
<template>
  <div class="crud-page household-page">
    <header class="page-heading">
      <div class="page-icon">
        <ListChecks :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>집안일·소모품</h1>
        <p>반복되는 집안일과 소모품 교체 주기를 관리합니다.</p>
      </div>
    </header>

    <!-- 조회·처리 결과와 체험 상태를 안내한다. -->
    <p v-if="isDemo" class="message demo-message">
      체험용 데이터입니다. 등록·수정·완료 처리는 로그인 후 이용할 수 있습니다.
    </p>
    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-if="notice" class="message success-message" role="status">
      {{ notice }}
    </p>

    <!-- 완료 입력 중에는 등록 폼을 숨겨 입력 대상을 구분한다. -->
    <section v-if="!completingTask" class="card">
      <div class="card-heading">
        <div class="heading-title">
          <Pencil v-if="editingId !== null" :size="20" aria-hidden="true" />
          <Plus v-else :size="20" aria-hidden="true" />
          <h2>{{ editingId !== null ? '항목 수정' : '항목 등록' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <form @submit.prevent="saveTask">
        <fieldset :disabled="loading || saving || historyLoading">
          <legend class="sr-only">집안일·소모품 입력</legend>

          <div class="form-grid">
            <label>
              항목명 *
              <input
                v-model="form.name"
                type="text"
                maxlength="100"
                placeholder="예: 욕실 청소, 주방 수세미 교체"
                required
              />
            </label>

            <label>
              유형 *
              <select v-model="form.taskType" required>
                <option value="CHORE">집안일</option>
                <option value="CONSUMABLE">소모품</option>
              </select>
            </label>

            <label>
              반복 주기(일) *
              <input
                v-model.number="form.cycleDays"
                type="number"
                min="1"
                max="2147483647"
                step="1"
                required
              />
            </label>

            <label>
              다음 예정일 *
              <input
                v-model="form.nextDueDate"
                type="date"
                min="0001-01-01"
                max="9999-12-31"
                required
              />
            </label>

            <label>
              사용 상태 *
              <select v-model="form.active">
                <option :value="true">사용 중</option>
                <option :value="false">비활성</option>
              </select>
            </label>

            <label class="memo-field">
              메모
              <textarea
                v-model="form.memo"
                rows="3"
                maxlength="500"
                placeholder="관리 방법이나 참고할 내용을 입력해 주세요."
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
              <Plus v-if="editingId === null" :size="16" aria-hidden="true" />
              <Pencil v-else :size="16" aria-hidden="true" />
              {{ saving ? '저장 중...' : editingId !== null ? '수정 저장' : '등록' }}
            </button>
          </div>
        </fieldset>
      </form>
    </section>

    <!-- 관리 목록에서 수정·삭제·완료·이력 조회를 선택한다. -->
    <section class="card" :aria-busy="loading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2>관리 목록</h2>
          <span v-if="!loading" class="count-badge"> {{ totalCount }}건 </span>
        </div>
        <!-- 항목명·관리 유형·사용 상태를 기준으로 검색한다. -->
        <form class="task-search" @submit.prevent="searchTasks">
          <label class="search-field">
            <span>항목명</span>
            <input
              v-model="searchName"
              type="search"
              placeholder="항목명 입력"
              :disabled="loading || saving || historyLoading"
            />
          </label>

          <label class="search-field status-field">
            <span>유형</span>
            <select v-model="searchTaskType" :disabled="loading || saving || historyLoading">
              <option value="">전체</option>
              <option value="CHORE">집안일</option>
              <option value="CONSUMABLE">소모품</option>
            </select>
          </label>

          <label class="search-field status-field">
            <span>상태</span>
            <select v-model="searchActive" :disabled="loading || saving || historyLoading">
              <option value="">전체</option>
              <option :value="true">사용 중</option>
              <option :value="false">비활성</option>
            </select>
          </label>

          <button
            type="submit"
            class="btn btn-primary"
            :disabled="loading || saving || historyLoading"
          >
            검색
          </button>
        </form>
      </div>

      <p class="section-description">
        완료 처리하면 완료일에 반복 주기를 더해 다음 예정일을 갱신합니다. 완료 이력이 있는 항목은
        삭제 대신 비활성화해 주세요.
      </p>

      <p v-if="loading" class="empty-state" role="status">목록을 불러오는 중입니다.</p>
      <p v-else-if="!HouseholdTasks.length" class="empty-state">
        {{ errorMessage ? '목록을 표시할 수 없습니다.' : '조회된 항목이 없습니다.' }}
      </p>

      <div v-else class="table-wrap">
        <table class="data-table task-table">
          <caption class="sr-only">
            집안일·소모품 관리 목록
          </caption>
          <thead>
            <tr>
              <th scope="col">항목</th>
              <th scope="col">유형</th>
              <th scope="col">주기</th>
              <th scope="col">다음 예정일</th>
              <th scope="col">상태</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="task in HouseholdTasks"
              :key="task.id"
              :class="{ 'editing-row': editingId === task.id }"
            >
              <td class="task-name-cell">
                <strong>{{ task.name }}</strong>
                <p v-if="task.memo" class="task-memo">{{ task.memo }}</p>
              </td>
              <td class="nowrap">{{ taskTypeLabel(task.taskType) }}</td>
              <td class="nowrap">{{ task.cycleDays }}일마다</td>
              <td class="date-cell">
                <span
                  :class="{
                    'overdue-date': task.active && task.nextDueDate < todayText(),
                  }"
                >
                  {{ task.nextDueDate }}
                </span>
                <small
                  v-if="task.active && task.nextDueDate < todayText()"
                  class="due-note overdue-date"
                >
                  예정일 지남
                </small>
                <small
                  v-else-if="task.active && task.nextDueDate === todayText()"
                  class="due-note today-date"
                >
                  오늘 예정
                </small>
              </td>
              <td>
                <span class="badge" :class="{ 'active-badge': task.active }">
                  {{ task.active ? '사용 중' : '비활성' }}
                </span>
              </td>
              <td class="manage-cell">
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-complete"
                    :disabled="loading || saving || historyLoading || !task.active"
                    @click="startComplete(task)"
                  >
                    완료
                  </button>
                  <button
                    type="button"
                    class="btn btn-secondary"
                    :disabled="loading || saving || historyLoading"
                    @click="showHistory(task)"
                  >
                    이력
                  </button>
                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="loading || saving || historyLoading"
                    @click="startEdit(task)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>
                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="loading || saving || historyLoading"
                    @click="deleteTask(task)"
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
        :disabled="loading || saving || historyLoading"
        @change="changePage"
      />
    </section>

    <!-- 선택한 항목의 실제 완료일과 메모를 입력한다. -->
    <section v-if="completingTask" class="card completion-card">
      <div class="card-heading">
        <div class="heading-title">
          <ListChecks :size="20" aria-hidden="true" />
          <h2>{{ completingTask.name }} · 완료 처리</h2>
        </div>
      </div>

      <p class="section-description">
        실제 완료한 날짜를 입력해 주세요. 같은 항목은 하루에 한 번 완료할 수 있습니다.
      </p>

      <form @submit.prevent="completeTask">
        <fieldset :disabled="loading || saving || historyLoading">
          <legend class="sr-only">완료 정보 입력</legend>

          <div class="form-grid">
            <label>
              완료일 *
              <input
                v-model="completion.completedDate"
                type="date"
                min="0001-01-01"
                :max="todayText()"
                required
              />
            </label>

            <label class="memo-field">
              완료 메모
              <textarea
                v-model="completion.memo"
                rows="3"
                maxlength="500"
                placeholder="완료한 내용이나 교체한 제품 등을 기록해 주세요."
              ></textarea>
            </label>
          </div>

          <div class="form-actions">
            <button type="button" class="btn btn-secondary" @click="cancelComplete">취소</button>
            <button type="submit" class="btn btn-primary">
              {{ saving ? '처리 중...' : '완료 저장' }}
            </button>
          </div>
        </fieldset>
      </form>
    </section>

    <!-- 이력은 사용자가 선택한 항목만 표시한다. -->
    <section v-if="historyTask" class="card" :aria-busy="historyLoading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2>{{ historyTask.name }} · 완료 이력</h2>
          <span v-if="!historyLoading" class="count-badge"> {{ historyTotalCount }}건 </span>
        </div>
        <button
          type="button"
          class="btn btn-secondary"
          :disabled="historyLoading || saving || loading"
          @click="closeHistory"
        >
          닫기
        </button>
      </div>

      <p v-if="historyLoading" class="empty-state" role="status">완료 이력을 불러오는 중입니다.</p>
      <p v-else-if="!HouseholdTaskHistorys.length" class="empty-state">
        {{ errorMessage ? '완료 이력을 표시할 수 없습니다.' : '완료 이력이 없습니다.' }}
      </p>

      <div v-else class="table-wrap">
        <table class="data-table history-table">
          <caption class="sr-only">
            선택한 항목의 완료 이력
          </caption>
          <thead>
            <tr>
              <th scope="col">완료일</th>
              <th scope="col">메모</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="history in HouseholdTaskHistorys" :key="history.id">
              <td class="date-cell">{{ history.completedDate }}</td>
              <td class="history-memo">{{ history.memo || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="historyPage"
        :total-pages="historyTotalPages"
        :disabled="loading || saving || historyLoading"
        @change="changeHistoryPage"
      />
    </section>
  </div>
</template>
<style scoped>
/* 공통 스타일에 없는 체험 안내와 화면별 보조 문구 */
.household-page .demo-message {
  background-color: #eff6ff;
  color: #1d4ed8;
}

.household-page .section-description {
  margin: 0 0 20px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

/* 목록이 좁아지면 표 영역 안에서 가로 스크롤한다. */
.household-page .task-table {
  min-width: 900px;
}

.household-page .task-name-cell {
  min-width: 180px;
  max-width: 280px;
  overflow-wrap: anywhere;
}

.household-page .task-memo {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.household-page .nowrap {
  white-space: nowrap;
}

.household-page .due-note {
  display: block;
  margin-top: 5px;
  font-size: 12px;
}

.household-page .overdue-date {
  color: #dc2626;
}

.household-page .today-date {
  color: #2563eb;
  font-weight: 600;
}

/* 사용 상태와 완료 버튼 */
.household-page .active-badge {
  background-color: #ecfdf5;
  color: #047857;
}

.household-page .btn-complete {
  background-color: #ecfdf5;
  color: #047857;
}

.household-page .btn-complete:hover:not(:disabled) {
  background-color: #d1fae5;
}

.household-page .completion-card {
  border-color: #93c5fd;
}

/* 완료 이력의 긴 메모도 표 안에서 줄바꿈한다. */
.household-page .history-table {
  min-width: 360px;
  table-layout: fixed;
}

.household-page .history-table th:first-child {
  width: 140px;
}

.household-page .history-memo {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 관리 목록 검색 조건을 오른쪽에 배치한다. */
.task-search {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.task-search .search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.task-search input,
.task-search select {
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

.task-search .status-field select {
  width: 120px;
}

.task-search input:focus-visible,
.task-search select:focus-visible {
  outline: 3px solid #dbeafe;
  outline-offset: 1px;
  border-color: #2563eb;
}

.task-search input:disabled,
.task-search select:disabled {
  background-color: #f8fafc;
  cursor: not-allowed;
}

.task-search .btn {
  min-height: 44px;
}

@media (max-width: 640px) {
  .household-page .heading-title h2 {
    overflow-wrap: anywhere;
  }

  .household-page .form-actions .btn {
    flex: 1;
  }
  .task-search {
    flex-direction: column;
    align-items: stretch;
  }

  .task-search label {
    display: grid;
    grid-template-columns: 60px minmax(0, 1fr);
  }

  .task-search input,
  .task-search select,
  .task-search button {
    width: 100%;
  }
}
</style>
