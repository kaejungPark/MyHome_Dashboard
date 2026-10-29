<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../api/client'
import type { PageResponse } from '../types/page'
import { MessageSquare, Search } from '@lucide/vue'
import { usePagination } from '../composables/usePagination'
import Pagination from '../components/Pagination.vue'
import { useErrorMessage } from '../composables/useErrorMessage'

// 게시글 목록 API에서 받는 한 건의 데이터 구조다.
// 상세 화면에서 사용하는 본문은 목록 응답에 포함하지 않는다.
interface Community {
  id: number // 커뮤니티 ID
  title: string // 제목
  nickname: string // 작성자 닉네임
  viewCount: number // 조회수
  createdDt: string // 작성일
}

// 검색 기준으로 허용할 값이다.
type SearchType = 'TITLE' | 'NICKNAME'

// 현재 페이지에 표시할 게시글 목록이다.
const communities = ref<Community[]>([])

// 목록 조회 중에는 검색과 페이지 이동의 중복 요청을 막는다.
const loading = ref(false)
const { errorMessage, showError } = useErrorMessage() // 화면의 오류 메시지와 공통 오류 처리 함수를 사용한다.

// 검색창에서 사용자가 선택·입력 중인 값이다.
const searchType = ref<SearchType>('TITLE')
const keyword = ref('')

// 검색 버튼을 눌러 실제 조회에 적용한 값이다.
// 페이지 이동 시에는 이 조건을 사용한다.
const appliedSearchType = ref<SearchType>('TITLE')
const appliedKeyword = ref('')

// 현재 페이지와 페이지당 개수, 서버에서 반환한 전체 개수·페이지 수를 관리한다.
const { page, size, totalCount, totalPages, resetPage, resetTotals, applyPage, canMoveTo } =
  usePagination(5)

const baseUrl = '/api/community'

/**
 * 확정된 검색 조건으로 게시글 목록을 조회한다.
 * 커뮤니티는 실제 게시글을 조회하므로 체험 데이터를 사용하지 않는다.
 * 삭제·숨김 등으로 현재 페이지가 사라지면 마지막 유효 페이지를 다시 조회한다.
 */
async function refreshCommunity() {
  communities.value = []
  resetTotals()

  // 재조회에서도 동일한 검색 조건과 페이지 크기를 사용한다.
  const currentSearchType = appliedSearchType.value
  const currentKeyword = appliedKeyword.value
  const pageSize = size.value

  // 지정한 페이지를 서버에서 조회한다.
  // 최초 조회와 마지막 유효 페이지 재조회에서 같은 요청 구성을 사용한다.
  async function fetchCommunityPage(targetPage: number): Promise<PageResponse<Community>> {
    // 검색어에 공백이나 특수문자가 있어도 URL에 안전하게 전달하도록 변환한다.
    const params = new URLSearchParams({
      page: String(targetPage),
      size: String(pageSize),
      searchType: currentSearchType,
      keyword: currentKeyword,
    })

    const result = await request<PageResponse<Community>>(`${baseUrl}?${params.toString()}`)

    if (result === null) {
      throw new Error('게시글 목록 응답이 없습니다.')
    }

    return result
  }

  let result = await fetchCommunityPage(page.value)

  // 결과가 없더라도 요청 페이지 번호는 최소 1로 유지한다.
  const lastPage = Math.max(1, result.totalPages)

  if (page.value > lastPage) {
    page.value = lastPage
    result = await fetchCommunityPage(lastPage)
  }

  // 서버가 반환한 현재 페이지의 게시글을 테이블에 표시한다.
  communities.value = result.items

  // 서버의 페이지 번호와 전체 건수를 공통 페이징 상태에 반영한다.
  applyPage(result)
}

// 새 검색은 항상 1페이지부터 조회한다.
async function searchCommunity() {
  if (loading.value) return

  appliedSearchType.value = searchType.value
  appliedKeyword.value = keyword.value.trim()
  resetPage()

  await loadData()
}

// UTC 작성 일시를 한국 시간 기준 날짜로 표시한다.
function formatCreatedDate(value: string): string {
  const utcValue = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`

  const date = new Date(utcValue)

  if (Number.isNaN(date.getTime())) return '—'

  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

// 적용된 검색 조건을 유지하면서 페이지를 이동한다.
async function changePage(targetPage: number) {
  if (loading.value) return
  if (!canMoveTo(targetPage)) return

  page.value = targetPage
  await loadData()
}

/**
 * 목록 조회 중 상태와 오류 메시지를 관리한다.
 * 실제 조회는 refreshCommunity에 맡기고,
 * 성공하거나 실패하더라도 마지막에 로딩 상태를 해제한다.
 */
async function loadData() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    await refreshCommunity()
  } catch (error: unknown) {
    showError(error)
  } finally {
    loading.value = false
  }
}

// 화면이 처음 표시되면 기본 검색 조건으로 게시글 목록을 조회한다.
onMounted(loadData)
</script>
<template>
  <section class="crud-page community-page">
    <header class="page-heading">
      <div class="page-icon">
        <MessageSquare :size="28" aria-hidden="true" />
      </div>
      <div>
        <h1>커뮤니티</h1>
        <p>자취 생활의 이야기와 유용한 정보를 나눠보세요.</p>
      </div>
    </header>

    <!-- 검색 버튼 또는 Enter로 검색. -->
    <section class="card" aria-label="게시글 검색">
      <form class="community-search" @submit.prevent="searchCommunity">
        <div class="form-grid search-fields">
          <label>
            <span class="sr-only">검색 기준</span>
            <select v-model="searchType" :disabled="loading">
              <option value="TITLE">제목</option>
              <option value="NICKNAME">닉네임</option>
            </select>
          </label>
          <label>
            <span class="sr-only">검색어</span>
            <input
              v-model="keyword"
              type="search"
              :placeholder="
                searchType === 'TITLE' ? '제목을 입력해 주세요' : '닉네임을 입력해 주세요'
              "
              :disabled="loading"
            />
          </label>
        </div>

        <div class="search-action">
          <span class="search-caption">검색기준</span>

          <button type="submit" class="btn btn-primary" :disabled="loading">
            <Search :size="16" aria-hidden="true" />
            {{ loading ? '조회 중…' : '검색' }}
          </button>
        </div>
      </form>
    </section>

    <!-- 조회 상태 안내 -->
    <p v-if="errorMessage" class="message error-message" role="alert">
      {{ errorMessage }}
    </p>
    <p v-else-if="loading" class="empty-state" role="status">게시글을 불러오는 중입니다.</p>
    <p v-else class="search-result" role="status">
      전체 {{ totalCount.toLocaleString('ko-KR') }}개의 게시글
    </p>

    <!-- 조회 성공 시 게시글 목록을 표시한다. -->
    <section v-if="!loading && !errorMessage" class="card" aria-labelledby="community-list-heading">
      <div class="card-heading">
        <h2 id="community-list-heading">게시글 목록</h2>
        <RouterLink :to="{ name: 'community-create' }" class="btn btn-primary write-link">
          글쓰기
        </RouterLink>
      </div>
      <p v-if="communities.length === 0" class="empty-state">게시글이 없습니다.</p>

      <div v-else class="table-wrap">
        <table class="data-table community-table">
          <caption class="sr-only">
            게시글 제목, 작성자, 조회수, 작성일
          </caption>

          <thead>
            <tr>
              <th scope="col">제목</th>
              <th scope="col">작성자</th>
              <th scope="col" class="view-count-cell">조회수</th>
              <th scope="col">작성일</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="community in communities" :key="community.id">
              <td class="post-title-cell">
                <RouterLink
                  :to="{ name: 'community-detail', params: { id: community.id } }"
                  class="post-title-link"
                >
                  {{ community.title }}
                </RouterLink>
              </td>
              <td>{{ community.nickname }}</td>
              <td class="view-count-cell">
                {{ community.viewCount.toLocaleString('ko-KR') }}
              </td>
              <td class="post-date-cell">
                {{ formatCreatedDate(community.createdDt) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <!-- 공통 페이지 버튼으로 게시글 목록의 페이지를 이동한다. -->
      <Pagination :page="page" :total-pages="totalPages" :disabled="loading" @change="changePage" />
    </section>
  </section>
</template>
<style scoped>
/* 검색 영역 전체를 카드 오른쪽에 배치한다. */
.community-search {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 8px;
}

/* 선택창 너비만 확보해 검색 버튼 바로 옆에 배치한다. */
.community-page .search-fields {
  flex: 0 0 130px;
  min-width: 0;
  grid-template-columns: 1fr;
}

.community-search .btn {
  min-height: 44px;
  flex-shrink: 0;
}

.search-result {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 14px;
}

.search-action {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.search-caption {
  color: #334155;
  font-size: 14px;
  text-align: center;
}

.community-table {
  min-width: 620px;
}

.community-table .post-title-cell {
  width: 55%;
  min-width: 240px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.community-table .view-count-cell {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.community-table .post-date-cell {
  white-space: nowrap;
}

.post-title-link {
  color: inherit;
  text-decoration: none;
}

.post-title-link:hover {
  color: #2563eb;
  text-decoration: underline;
}

.post-title-link:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 3px;
}

.write-link {
  text-decoration: none;
}

/* 검색 기준과 검색어 입력창을 검색 버튼 왼쪽에 나란히 배치한다. */
.community-page .search-fields {
  flex: 0 1 450px;
  min-width: 0;
  grid-template-columns: 130px minmax(0, 1fr);
}

/* 모바일에서는 검색창과 버튼을 세로로 배치한다. */
@media (max-width: 560px) {
  .community-search {
    flex-direction: column;
    align-items: stretch;
  }

  .community-page .search-fields {
    flex: none;
    width: 100%;
    grid-template-columns: 1fr;
  }
}
</style>
