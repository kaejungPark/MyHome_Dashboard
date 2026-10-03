<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { Package, Plus, Pencil, Trash2, RefreshCw, List } from '@lucide/vue' // 물품 관리 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'

// 물품 상태에 사용할 수 있는 값이다.
type HomeItemStatus = 'IN_USE' | 'STORED' | 'DISPOSED'

// 물품 목록 조회와 수정 폼에서 사용하는 데이터다.
interface HomeItem {
  id: number // 물품 ID
  name: string // 물품명
  category: string | null // 분류
  purchaseDate: string | null // 구매일
  purchasePrice: number | null // 구매 금액
  warrantyEndDate: string | null // 보증 종료일
  maintenanceCycle: number | null // 관리 주기: 일 단위
  status: HomeItemStatus // 물품 상태
  memo: string | null // 메모
}

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)
const { canModify } = useDemoGuard(isDemo)

// 조회 결과와 화면 처리 상태를 관리한다.
const homeItems = ref<HomeItem[]>([])
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const editingId = ref<number | null>(null) // null이면 신규 등록
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 완료 메시지

const baseUrl = '/api/home-item'

// 선택 입력은 빈 문자열로 초기화하고, 저장할 때 null로 변환한다.
function initialForm() {
  return {
    name: '',
    category: '',
    purchaseDate: '',
    purchasePrice: '',
    warrantyEndDate: '',
    maintenanceCycle: '',
    status: 'IN_USE' as HomeItemStatus,
    memo: '',
  }
}

// 폼 입력값을 화면과 동기화한다.
const form = reactive(initialForm())

// 서버의 상태 코드를 화면에 표시할 한글 명칭으로 변환한다.
function statusLabel(status: HomeItemStatus): string {
  switch (status) {
    case 'IN_USE':
      return '사용 중'
    case 'STORED':
      return '보관 중'
    case 'DISPOSED':
      return '처분'
    default:
      return status
  }
}

// 오류를 개발자 도구에 기록하고 화면에 안내 메시지를 표시한다.
function showError(error: unknown) {
  console.error(error)
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 현재 사용자의 물품 목록을 조회한다.
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  homeItems.value = []
  isDemo.value = false

  try {
    const demo = createDemoData()

    const [homeItemResult] = await Promise.all([
      requestWithDemo<HomeItem[]>(baseUrl, () => demo.items),
    ])

    homeItems.value = homeItemResult.data
    isDemo.value = homeItemResult.isDemo
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 수정 상태를 해제하고 신규 등록 폼으로 되돌린다.
function resetForm() {
  editingId.value = null
  Object.assign(form, initialForm())
}

// 선택한 물품을 수정 폼에 채운다.
// 서버의 null 값은 입력란에서 사용할 빈 문자열로 변환한다.
function startEdit(item: HomeItem) {
  if (saving.value || loading.value) return

  editingId.value = item.id
  errorMessage.value = ''
  notice.value = ''

  Object.assign(form, {
    name: item.name,
    category: item.category ?? '',
    purchaseDate: item.purchaseDate ?? '',
    purchasePrice: item.purchasePrice === null ? '' : String(item.purchasePrice),
    warrantyEndDate: item.warrantyEndDate ?? '',
    maintenanceCycle: item.maintenanceCycle === null ? '' : String(item.maintenanceCycle),
    status: item.status,
    memo: item.memo ?? '',
  })

  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 신규 등록은 POST, 기존 물품 수정은 PUT으로 저장한다.
async function saveHomeItem() {
  if (saving.value || loading.value) return
  if (!canModify()) return

  errorMessage.value = ''
  notice.value = ''

  // 선택 입력인 숫자는 빈칸과 0을 구분한다.
  const priceText = form.purchasePrice.trim()
  const cycleText = form.maintenanceCycle.trim()
  const purchasePrice = priceText === '' ? null : Number(priceText)
  const maintenanceCycle = cycleText === '' ? null : Number(cycleText)

  // 물품명과 상태는 필수 입력이다.
  if (!form.name.trim() || !['IN_USE', 'STORED', 'DISPOSED'].includes(form.status)) {
    errorMessage.value = '물품명과 상태를 확인해 주세요.'
    return
  }

  // 화면에서는 구매 금액을 원 단위 정수로 입력받는다.
  if (purchasePrice !== null && (!Number.isSafeInteger(purchasePrice) || purchasePrice < 0)) {
    errorMessage.value = '구매 금액은 0 이상의 정수로 입력해 주세요.'
    return
  }

  // 관리 주기는 DB의 INT 범위 안에서 1일 이상이어야 한다.
  if (
    maintenanceCycle !== null &&
    (!Number.isInteger(maintenanceCycle) || maintenanceCycle < 1 || maintenanceCycle > 2147483647)
  ) {
    errorMessage.value = '관리 주기는 1~2147483647 사이의 정수로 입력해 주세요.'
    return
  }

  // 두 날짜가 모두 입력된 경우에만 순서를 검사한다.
  // 날짜 입력값은 YYYY-MM-DD 형식이므로 문자열로 비교할 수 있다.
  if (form.purchaseDate && form.warrantyEndDate && form.warrantyEndDate < form.purchaseDate) {
    errorMessage.value = '보증 종료일은 구매일보다 빠를 수 없습니다.'
    return
  }

  const id = editingId.value
  saving.value = true

  try {
    await request<null>(id === null ? baseUrl : `${baseUrl}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: form.name.trim(),
        category: form.category.trim() || null,
        purchaseDate: form.purchaseDate || null,
        purchasePrice,
        warrantyEndDate: form.warrantyEndDate || null,
        maintenanceCycle,
        status: form.status,
        memo: form.memo.trim() || null,
      }),
    })

    // 저장 성공 후 폼을 초기화하고 최신 목록을 조회한다.
    resetForm()
    notice.value = id === null ? '등록됐습니다.' : '수정됐습니다.'
    await loadData()
  } catch (error: unknown) {
    // 저장 요청이 실패하면 입력 내용을 유지한다.
    showError(error)
  } finally {
    saving.value = false
  }
}

// 사용자 확인 후 물품을 삭제하고 목록을 갱신한다.
async function deleteHomeItem(item: HomeItem) {
  if (saving.value || loading.value) return
  if (!canModify()) return
  if (!window.confirm(`"${item.name}" 물품을 삭제하시겠습니까?`)) return

  saving.value = true
  errorMessage.value = ''
  notice.value = ''

  try {
    await request<null>(`${baseUrl}/${item.id}`, {
      method: 'DELETE',
    })

    // 수정 중인 물품을 삭제했다면 수정 상태도 해제한다.
    if (editingId.value === item.id) {
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

// 화면이 처음 표시되면 물품 목록을 조회한다.
onMounted(loadData)
</script>
<template>
  <section class="crud-page item-page">
    <header class="page-heading">
      <div class="page-icon">
        <Package :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>물품 관리</h1>
        <p>집 안 물품의 구매 정보와 보증 기간, 관리 주기를 확인합니다.</p>
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

    <form class="card" @submit.prevent="saveHomeItem">
      <div class="card-heading">
        <div class="heading-title">
          <component :is="editingId === null ? Plus : Pencil" :size="20" aria-hidden="true" />
          <h2>{{ editingId === null ? '물품 등록' : '물품 수정' }}</h2>
        </div>
        <span class="heading-note">* 필수 입력</span>
      </div>

      <fieldset :disabled="saving || loading">
        <legend class="sr-only">물품 관리 입력</legend>
        <div class="form-grid">
          <label>
            물품명 *
            <input v-model="form.name" maxlength="200" required />
          </label>

          <label>
            분류
            <input v-model="form.category" maxlength="100" placeholder="가전, 가구 등" />
          </label>

          <label>
            구매일
            <input v-model="form.purchaseDate" type="date" />
          </label>

          <label>
            구매 금액
            <input
              v-model="form.purchasePrice"
              type="text"
              inputmode="numeric"
              pattern="[0-9]+"
              placeholder="금액이 없으면 비워 주세요"
            />
          </label>

          <label>
            보증 종료일
            <input
              v-model="form.warrantyEndDate"
              type="date"
              :min="form.purchaseDate || undefined"
            />
          </label>

          <label>
            관리 주기
            <input
              v-model="form.maintenanceCycle"
              type="text"
              inputmode="numeric"
              pattern="[0-9]+"
              placeholder="일 단위, 예: 90"
            />
          </label>

          <label>
            상태 *
            <select v-model="form.status" required>
              <option value="IN_USE">사용 중</option>
              <option value="STORED">보관 중</option>
              <option value="DISPOSED">처분</option>
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

    <section class="card" aria-labelledby="item-list-heading">
      <div class="card-heading">
        <div class="heading-title">
          <List :size="20" aria-hidden="true" />
          <h2 id="item-list-heading">물품 목록</h2>
          <span v-if="!loading" class="count-badge"> {{ homeItems.length }}건</span>
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

      <p v-if="loading" class="empty-state" role="status">조회 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="homeItems.length === 0" class="empty-state">등록된 물품이 없습니다.</p>

      <div v-if="!loading && homeItems.length > 0" class="table-wrap">
        <table class="data-table item-table">
          <caption class="sr-only">
            등록된 물품
          </caption>
          <thead>
            <tr>
              <th scope="col">물품명</th>
              <th scope="col">분류</th>
              <th scope="col">구매일</th>
              <th scope="col" class="amount-cell">구매 금액</th>
              <th scope="col">보증 종료일</th>
              <th scope="col">관리 주기</th>
              <th scope="col">상태</th>
              <th scope="col" class="manage-cell">관리</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="homeItem in homeItems"
              :key="homeItem.id"
              :class="{ 'editing-row': editingId === homeItem.id }"
            >
              <td class="title-cell">{{ homeItem.name }}</td>
              <td>{{ homeItem.category || '-' }}</td>
              <td class="date-cell">{{ homeItem.purchaseDate ?? '-' }}</td>
              <td class="amount-cell">
                {{
                  homeItem.purchasePrice === null
                    ? '-'
                    : `${homeItem.purchasePrice.toLocaleString('ko-KR')}원`
                }}
              </td>
              <td class="date-cell">{{ homeItem.warrantyEndDate ?? '-' }}</td>
              <td>
                {{ homeItem.maintenanceCycle === null ? '-' : `${homeItem.maintenanceCycle}일` }}
              </td>
              <td>
                <span class="badge">
                  {{ statusLabel(homeItem.status) }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-edit"
                    :disabled="saving || loading"
                    :aria-label="`${homeItem.name} 수정`"
                    @click="startEdit(homeItem)"
                  >
                    <Pencil :size="14" aria-hidden="true" />
                    수정
                  </button>
                  <button
                    type="button"
                    class="btn btn-delete"
                    :disabled="saving || loading"
                    :aria-label="`${homeItem.name} 삭제`"
                    @click="deleteHomeItem(homeItem)"
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
.item-table {
  min-width: 740px;
}

.item-table .title-cell {
  min-width: 150px;
  max-width: 280px;
  font-weight: 600;
  overflow-wrap: anywhere;
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
