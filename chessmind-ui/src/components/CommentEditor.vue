<script setup lang="ts">
import { ref, watch } from 'vue'
import { useSessionStore } from '@/stores/session'

const store = useSessionStore()
const draft = ref(store.currentComment)
let savedValue = store.currentComment

watch(
  () => store.currentComment,
  (val) => {
    draft.value = val
    savedValue = val
  },
)

async function onBlur() {
  if (store.isLoading || draft.value === savedValue) return
  store.currentComment = draft.value
  savedValue = draft.value
  await store.saveComment()
}
</script>

<template>
  <textarea
    v-model="draft"
    class="comment-textarea"
    data-testid="comment-editor"
    placeholder="Add a comment…"
    rows="3"
    :disabled="store.isLoading"
    @blur="onBlur"
  />
</template>

<style scoped>
.comment-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 0.5rem 0.75rem;
  font-family: sans-serif;
  font-size: 0.9rem;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  resize: vertical;
  color: #2c3e50;
  background: #fff;
}

.comment-textarea:focus {
  outline: none;
  border-color: #adb5bd;
}

.comment-textarea:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
