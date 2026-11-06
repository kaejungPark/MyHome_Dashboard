<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
/**
 * 부모 화면이 조회한 현재 페이지와 전체 페이지 수를 전달받는다.
 * disabled가 true이면 페이지 이동을 막고, 생략하면 이동을 허용한다.
 */
const props = withDefaults(
  defineProps<{
    page: number // 현재 화면에 표시 중인 페이지 번호
    totalPages: number // 검색 결과의 전체 페이지 수
    disabled?: boolean // 조회·저장 중 페이지 이동을 잠글 때 사용
  }>(),
  {
    disabled: false,
  },
)

// 조회는 부모 화면에서 처리하고, 이동할 페이지 번호만 전달한다.
const emit = defineEmits<{
  change: [page: number]
}>()

/**
 * 이동 가능한 페이지인지 확인한 뒤 부모 화면에 change 이벤트를 전달한다.
 * 부모 화면은 전달받은 페이지 번호로 목록을 다시 조회한다.
 */
function movePage(targetPage: number) {
  if (props.disabled) return
  if (targetPage < 1 || targetPage > props.totalPages) return
  if (targetPage === props.page) return

  emit('change', targetPage)
}
</script>

<template>
  <!-- 조회 결과에 페이지가 있을 때 이전·다음 버튼과 현재 페이지를 표시한다. -->
  <nav v-if="totalPages > 0" class="pagination" aria-label="목록 페이지 이동">
    <button type="button" :disabled="disabled || page <= 1" @click="movePage(page - 1)">
      이전
    </button>

    <span class="page-info" aria-live="polite"> {{ page }} / {{ totalPages }} 페이지 </span>

    <button type="button" :disabled="disabled || page >= totalPages" @click="movePage(page + 1)">
      다음
    </button>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}

.pagination button {
  min-height: 38px;
  padding: 8px 14px;
  border: 1px solid #dce3ed;
  border-radius: 7px;
  background-color: #fff;
  color: #475569;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.pagination button:hover:not(:disabled) {
  border-color: #2563eb;
  background-color: #eff6ff;
  color: #2563eb;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 키보드로 이동할 때 현재 선택된 버튼을 테두리로 표시한다. */
.pagination button:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 2px;
}

.page-info {
  color: #475569;
  font-size: 14px;
}
</style>
