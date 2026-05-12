<script setup lang="ts">
import { useSessionStore } from '@/stores/session'

const store = useSessionStore()
</script>

<template>
  <div class="resume-banner" data-testid="resume-banner">
    <button
      type="button"
      class="resume-btn"
      :disabled="store.isLoading"
      @click="store.resumeStudy()"
    >
      Resume Study →
    </button>
  </div>
</template>

<style scoped>
.resume-banner {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: flex-end;
  padding: 0.6rem 1rem;
  background: #e8f4fd;
  border: 1px solid #bee3f8;
  border-radius: 6px;
}

.resume-btn {
  padding: 0.45rem 1.1rem;
  font-size: 0.95rem;
  font-weight: 600;
  background: #2b6cb0;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.resume-btn:hover:not(:disabled) {
  background: #2c5282;
}

.resume-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
