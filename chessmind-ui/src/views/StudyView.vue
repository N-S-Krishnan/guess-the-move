<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { useVariationKeyNav } from '@/composables/useVariationKeyNav'
import AnalysisPanel from '@/components/AnalysisPanel.vue'
import ChessBoard from '@/components/ChessBoard.vue'
import ExportButton from '@/components/ExportButton.vue'
import MoveGuessPanel from '@/components/MoveGuessPanel.vue'
import MoveList from '@/components/MoveList.vue'
import StudyComplete from '@/components/StudyComplete.vue'
import type { Color } from 'chessground/types'

const router = useRouter()
const store = useSessionStore()
useVariationKeyNav()

onMounted(() => {
  if (!store.currentFen) {
    router.replace({ name: 'home' })
  }
})

watch(
  () => store.currentFen,
  (fen) => {
    if (!fen) router.replace({ name: 'home' })
  },
)

// Reset the board when returning to guess mode from analysis so chessground's
// internal position (which may have advanced through analysis moves) is
// snapped back to the mainline FEN and interactive config is restored.
watch(
  () => store.mode,
  async (newMode, oldMode) => {
    if (oldMode === 'analysis' && newMode === 'guess') {
      await nextTick()
      boardRef.value?.reset?.()
    }
  },
)

const orientationMap: Record<string, Color> = { white: 'white', black: 'black' }
const orientation = orientationMap[store.playerToGuess ?? 'white'] ?? 'white'

const boardRef = ref<{ reset: () => void } | null>(null)

const boardFen = computed(() => store.activeAnalysisFen ?? '')

async function handleMove(uci: string): Promise<void> {
  if (store.mode === 'guess') {
    const result = await store.submitGuess(uci)
    if (result?.correct !== true) {
      await nextTick()
      boardRef.value?.reset?.()
    }
  } else if (store.mode === 'analysis') {
    await store.addAnalysisMove(uci)
    if (store.serverError) {
      await nextTick()
      boardRef.value?.reset?.()
    }
  }
}
</script>

<template>
  <main v-if="store.currentFen" class="study-view">
    <div class="study-board-wrap">
      <ChessBoard
        ref="boardRef"
        :fen="boardFen"
        :orientation="orientation"
        :interactive="(store.mode === 'guess' || store.mode === 'analysis') && !store.isLoading && !store.isAnimating"
        @move="handleMove"
      />
    </div>

    <div
      v-if="store.serverError"
      class="server-error"
      data-testid="server-error"
      @click="store.clearError()"
    >
      {{ store.serverError }}
    </div>

    <MoveGuessPanel v-if="store.mode === 'guess'" />
    <div
      v-else-if="store.mode === 'reviewing'"
      class="reviewing-panel"
      data-testid="reviewing-panel"
    >
      <button
        type="button"
        class="next-btn"
        data-testid="next-btn"
        @click="store.nextPosition()"
      >
        Analyze from position →
      </button>
    </div>
    <AnalysisPanel v-else-if="store.mode === 'analysis'" />
    <StudyComplete v-else-if="store.mode === 'complete'" />

    <MoveList />

    <aside class="study-info">
      <template v-if="store.mode !== 'complete'">
        <p class="study-label">
          Guessing as
          <strong>{{ store.playerToGuess === 'white' ? 'White' : 'Black' }}</strong>
        </p>
        <p class="study-label">
          Move
          <strong>{{ store.currentMoveNumber }}</strong>
        </p>
      </template>
      <ExportButton />
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
  align-items: center;
  gap: 2rem;
  width: min(560px, 100%);
  justify-content: space-between;
}

.study-label {
  margin: 0;
  color: #555;
  font-size: 0.95rem;
}

.study-label strong {
  color: #2c3e50;
}

.server-error {
  width: min(560px, 100%);
  padding: 0.75rem 1rem;
  background: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  color: #721c24;
  font-size: 0.9rem;
  cursor: pointer;
}

.reviewing-panel {
  width: min(560px, 100%);
  padding: 1rem 1.25rem;
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  display: flex;
  justify-content: flex-end;
}

.next-btn {
  padding: 0.45rem 1.1rem;
  font-size: 0.95rem;
  font-weight: 600;
  background: #2c3e50;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.next-btn:hover {
  background: #3d5166;
}
</style>
