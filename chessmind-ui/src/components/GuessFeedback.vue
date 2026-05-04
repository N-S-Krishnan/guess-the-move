<script setup lang="ts">
import { ref, watch } from 'vue'
import { useSessionStore } from '@/stores/session'

const MAX_ATTEMPTS = 3

const store = useSessionStore()
const attempts = ref(0)

watch(
  () => store.lastGuessResult,
  (result) => {
    if (result === null) {
      attempts.value = 0
    } else {
      attempts.value++
      if (attempts.value >= MAX_ATTEMPTS) {
        store.revealMove()
      }
    }
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="attempts > 0" class="guess-feedback" data-testid="guess-feedback">
    <p class="feedback-message" data-testid="feedback-message">Not the move — try again</p>
    <p class="attempt-count" data-testid="attempt-count">Attempt {{ attempts }} of {{ MAX_ATTEMPTS }}</p>
    <button
      type="button"
      class="reveal-btn"
      data-testid="reveal-btn"
      @click="store.revealMove()"
    >
      Give up
    </button>
  </div>
</template>

<style scoped>
.guess-feedback {
  margin-top: 0.75rem;
  padding: 0.75rem 1rem;
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
}

.feedback-message {
  margin: 0 0 0.5rem;
  color: #856404;
  font-size: 0.9rem;
}

.reveal-btn {
  padding: 0.35rem 0.9rem;
  font-size: 0.9rem;
  background: #6c757d;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
