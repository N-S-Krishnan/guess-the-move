<script setup lang="ts">
import { useSessionStore } from '@/stores/session'

const store = useSessionStore()
const SYMBOLS = ['!', '?', '!!', '??', '!?', '?!'] as const
</script>

<template>
  <div class="symbol-selector" data-testid="symbol-selector">
    <button
      v-for="sym in SYMBOLS"
      :key="sym"
      type="button"
      class="symbol-btn"
      :class="{ 'symbol-btn--active': store.currentSymbol === sym }"
      :disabled="store.isLoading"
      @click="store.toggleSymbol(sym)"
    >
      {{ sym }}
    </button>
  </div>
</template>

<style scoped>
.symbol-selector {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.symbol-btn {
  padding: 0.25rem 0.55rem;
  font-size: 0.95rem;
  font-weight: 600;
  background: #fff;
  border: 1px solid #ced4da;
  border-radius: 4px;
  cursor: pointer;
  color: #495057;
  font-family: monospace;
  line-height: 1.4;
}

.symbol-btn:hover:not(:disabled) {
  background: #f1f3f5;
  border-color: #adb5bd;
}

.symbol-btn--active {
  background: #2c3e50;
  color: #fff;
  border-color: #2c3e50;
}

.symbol-btn--active:hover:not(:disabled) {
  background: #3d5166;
  border-color: #3d5166;
}

.symbol-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
