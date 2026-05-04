<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/session'

const store = useSessionStore()

interface FullMove {
  number: number
  whiteSan: string
  blackSan: string | null  // null when the game ends on White's move
  revealed: boolean
}

const fullMoves = computed((): FullMove[] => {
  const moveSans = store.moves
  if (!moveSans || moveSans.length === 0) return []

  // Reveal everything once the session is complete; otherwise reveal all full
  // moves strictly before the current guess position.
  const cutoff =
    store.mode === 'complete' ? Infinity : (store.currentMoveNumber ?? 1) - 1

  const fullMoveCount = Math.ceil(moveSans.length / 2)
  return Array.from({ length: fullMoveCount }, (_, i) => ({
    number: i + 1,
    whiteSan: moveSans[2 * i]!,
    blackSan: 2 * i + 1 < moveSans.length ? moveSans[2 * i + 1]! : null,
    revealed: i + 1 <= cutoff,
  }))
})
</script>

<template>
  <div v-if="fullMoves.length > 0" class="move-list" data-testid="move-list">
    <div
      v-for="fm in fullMoves"
      :key="fm.number"
      class="move-row"
      :data-testid="`move-row-${fm.number}`"
    >
      <span class="move-number">{{ fm.number }}.</span>

      <span v-if="fm.revealed" class="move-san" :data-testid="`white-san-${fm.number}`">
        {{ fm.whiteSan }}
      </span>
      <span v-else class="move-mask" :data-testid="`white-mask-${fm.number}`">…</span>

      <template v-if="fm.blackSan !== null">
        <span v-if="fm.revealed" class="move-san" :data-testid="`black-san-${fm.number}`">
          {{ fm.blackSan }}
        </span>
        <span v-else class="move-mask" :data-testid="`black-mask-${fm.number}`">…</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.move-list {
  width: min(560px, 100%);
  padding: 0.75rem 1.25rem;
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  font-size: 0.9rem;
  font-family: monospace;
  max-height: 240px;
  overflow-y: auto;
}

.move-row {
  display: flex;
  gap: 0.75rem;
  padding: 0.15rem 0;
  align-items: baseline;
}

.move-number {
  color: #888;
  min-width: 2rem;
  text-align: right;
}

.move-san {
  min-width: 4rem;
  color: #2c3e50;
}

.move-mask {
  min-width: 4rem;
  color: #bbb;
  user-select: none;
}
</style>
