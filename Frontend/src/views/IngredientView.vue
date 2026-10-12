<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { request, requestWithDemo } from '../api/client'
import { createDemoData } from '../mocks/demoData'
import { Carrot, Plus, Pencil, Trash2, RefreshCw, List } from '@lucide/vue' // 물품 관리 화면의 제목과 주요 동작에 사용할 아이콘
import { useDemoGuard } from '../composables/useDemoGuard'

// 식재료 보관 장소 상태값
type IngredientStorageLocation = 'REFRIGERATOR' | 'FREEZER' | 'PANTRY'
// 식재료 상태값
type IngredientStatus = 'STORED' | 'CONSUMED' | 'DISPOSED'

// 식재료 목록 조회와 수정 폼에서 사용하는 데이터다.
interface Ingredient {
  id: number
  name: string
  quantity: number
  unit: string
  storageLocation: IngredientStorageLocation
  purchaseDate: string | null
  expirationDate: string | null
  status: IngredientStatus
  memo: string | null
}

// 비로그인 체험 상태에서만 사용한다.
const isDemo = ref(false)
const { canModify } = useDemoGuard(isDemo)

const Ingredients = ref<Ingredient[]>([])
const loading = ref(false) // 조회 중 여부
const saving = ref(false) // 저장·삭제 처리 중 여부
const editingId = ref<number | null>(null) // null이면 신규 등록
const errorMessage = ref('') // 오류 메시지
const notice = ref('') // 처리 완료 메시지

const baseUrl = '/api/ingredients'

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

// 폼 입력값을 화면과 동기화한다.
const form = reactive(initialForm())

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

// 오류를 개발자 도구에 기록하고 화면에 안내 메시지를 표시한다.
function showError(error: unknown) {
  console.error(error)
  errorMessage.value = error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

// 로그인 상태에서는 개인 물품 목록을, 비로그인 상태에서는 체험용 물품 목록을 표시한다.
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''
  Ingredients.value = []
  isDemo.value = false

  try {
    const demo = createDemoData()

    const [result] = await Promise.all([
      requestWithDemo<Ingredient[]>(baseUrl, () => demo.ingredients),
    ])

    Ingredients.value = result.data
    isDemo.value = result.isDemo
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

// 선택한 식재료를 수정 폼에 채운다.
function startEdit(item: Ingredient) {
  if (saving.value || loading.value) return
  if (!canModify()) return

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

// 신규 등록은 POST, 기존 식재료 수정은 PUT으로 저장한다.
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

    // 저장 성공 후 수정 상태를 해제하고 최신 목록을 조회한다.
    editingId.value = null
    Object.assign(form, initialForm())
    notice.value = id === null ? '식재료가 등록됐습니다.' : '식재료가 수정됐습니다.'

    await loadData()
  } catch (error: unknown) {
    // 저장 요청이 실패하면 입력 내용을 유지한다.
    showError(error)
  } finally {
    saving.value = false
  }
}

// 본인 식재료를 삭제하고 목록을 갱신한다.
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
          <span v-if="!loading" class="count-badge"> {{ Ingredients.length }}건 </span>
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

      <p v-if="loading" class="empty-state" role="status">식재료를 불러오는 중입니다.</p>
      <p v-else-if="errorMessage" class="list-warning">목록이 최신 상태가 아닐 수 있습니다.</p>
      <p v-else-if="Ingredients.length === 0" class="empty-state">등록된 식재료가 없습니다.</p>

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
              <td class="date-cell">{{ item.expirationDate ?? '미입력' }}</td>

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

@media (max-width: 560px) {
  .ingredient-page .card-heading {
    flex-wrap: wrap;
    gap: 12px;
  }
}
</style>
