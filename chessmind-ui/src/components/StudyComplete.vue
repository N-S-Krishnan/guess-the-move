<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const store = useSessionStore()
const router = useRouter()

const playerLabel = computed(() => (store.playerToGuess === 'white' ? 'White' : 'Black'))

const movesStudied = computed(() => {
  const total = store.moves?.length ?? 0
  if (total === 0) return 0
  return store.playerToGuess === 'white'
    ? Math.ceil(total / 2)
    : Math.floor(total / 2)
})

function studyAnother(): void {
  router.push({ name: 'import' })
}
</script>

<template>
  <div class="study-complete" data-testid="study-complete">
    <p class="complete-heading" data-testid="complete-heading">Study complete!</p>
    <p class="complete-detail" data-testid="complete-detail">
      You guessed all {{ movesStudied }}
      <strong>{{ playerLabel }}</strong> move{{ movesStudied === 1 ? '' : 's' }}.
    </p>
    <button
      type="button"
      class="another-btn"
      data-testid="study-another-btn"
      @click="studyAnother"
    >
      Study another game
    </button>
  </div>
</template>

<style scoped>
.study-complete {
  width: min(560px, 100%);
  padding: 1.5rem 1.25rem;
  background: #d4edda;
  border: 1px solid #c3e6cb;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.complete-heading {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 700;
  color: #155724;
}

.complete-detail {
  margin: 0;
  font-size: 0.95rem;
  color: #155724;
}

.another-btn {
  align-self: flex-start;
  padding: 0.5rem 1.25rem;
  font-size: 0.95rem;
  background: #155724;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
