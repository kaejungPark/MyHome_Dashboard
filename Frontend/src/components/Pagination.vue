<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
// 페이지 정보는 부모 화면에서 전달받는다.
const props = withDefaults(
  defineProps<{
    page: number
    totalPages: number
    disabled?: boolean
  }>(),
  {
    disabled: false,
  },
)

// 조회는 부모 화면에서 처리하고, 이동할 페이지 번호만 전달한다.
const emit = defineEmits<{
  change: [page: number]
}>()

function movePage(targetPage: number) {
  if (props.disabled) return
  if (targetPage < 1 || targetPage > props.totalPages) return
  if (targetPage === props.page) return

  emit('change', targetPage)
}
</script>

<template>
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

.pagination button:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 2px;
}

.page-info {
  color: #475569;
  font-size: 14px;
}
</style>
