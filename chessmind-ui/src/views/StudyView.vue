<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import ChessBoard from '@/components/ChessBoard.vue'
import type { Color } from 'chessground/types'

const router = useRouter()
const store = useSessionStore()

// Guard: session must have been set up before arriving here.
onMounted(() => {
  if (!store.currentFen) {
    router.replace({ name: 'import' })
  }
})

const orientationMap: Record<string, Color> = { white: 'white', black: 'black' }
const orientation = orientationMap[store.playerToGuess ?? 'white'] ?? 'white'
</script>

<template>
  <main v-if="store.currentFen" class="study-view">
    <div class="study-board-wrap">
      <ChessBoard :fen="store.currentFen" :orientation="orientation" />
    </div>

    <aside class="study-info">
      <p class="study-label">
        Guessing as
        <strong>{{ store.playerToGuess === 'white' ? 'White' : 'Black' }}</strong>
      </p>
      <p class="study-label">
        Starting from move
        <strong>{{ store.currentMoveNumber }}</strong>
      </p>
    </aside>
  </main>
</template>

<style scoped>
.study-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 2rem 1.5rem;
}

.study-board-wrap {
  width: min(560px, 100%);
}

.study-info {
  display: flex;
  gap: 2rem;
}

.study-label {
  margin: 0;
  color: #555;
  font-size: 0.95rem;
}

.study-label strong {
  color: #2c3e50;
}
</style>
