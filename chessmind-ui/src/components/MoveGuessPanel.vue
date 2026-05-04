<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/session'
import GuessFeedback from './GuessFeedback.vue'

const store = useSessionStore()

const playerLabel = computed(() => (store.playerToGuess === 'white' ? 'White' : 'Black'))
</script>

<template>
  <div class="move-guess-panel" data-testid="move-guess-panel">
    <div v-if="store.lastGuessWasCorrect" class="correct-banner" data-testid="correct-banner">
      Correct! Opponent is playing…
    </div>
    <template v-else>
      <p class="guess-prompt" data-testid="guess-prompt">
        Move <strong>{{ store.currentMoveNumber }}</strong> — guess
        <strong>{{ playerLabel }}</strong>'s move
      </p>
      <GuessFeedback v-if="store.lastGuessResult !== null" />
    </template>
  </div>
</template>

<style scoped>
.move-guess-panel {
  width: min(560px, 100%);
  padding: 1rem 1.25rem;
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 6px;
}

.guess-prompt {
  margin: 0;
  font-size: 1rem;
  color: #2c3e50;
}

.correct-banner {
  font-size: 1rem;
  font-weight: 600;
  color: #155724;
  background: #d4edda;
  border: 1px solid #c3e6cb;
  border-radius: 4px;
  padding: 0.6rem 1rem;
}
</style>
