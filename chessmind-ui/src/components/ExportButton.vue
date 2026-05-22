<script setup lang="ts">
import { ref } from 'vue'
import { useSessionStore } from '@/stores/session'
import { exportSession } from '@/services/sessionService'

const store = useSessionStore()
const isExporting = ref(false)

async function handleExport(): Promise<void> {
  if (!store.sessionId) return
  isExporting.value = true
  try {
    const blob = await exportSession(store.sessionId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `chessmind-${store.sessionId}.pgn`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    // error is non-critical; silently ignore so the study isn't disrupted
  } finally {
    isExporting.value = false
  }
}
</script>

<template>
  <button
    type="button"
    class="export-btn"
    :disabled="!store.sessionId || isExporting"
    data-testid="export-btn"
    @click="handleExport"
  >
    {{ isExporting ? 'Exporting…' : 'Export PGN' }}
  </button>
</template>

<style scoped>
.export-btn {
  padding: 0.4rem 1rem;
  font-size: 0.9rem;
  font-weight: 500;
  background: #f0f4f8;
  color: #2c3e50;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}

.export-btn:hover:not(:disabled) {
  background: #e2e8f0;
}

.export-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
