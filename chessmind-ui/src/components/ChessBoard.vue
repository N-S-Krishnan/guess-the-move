<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { Chessground } from 'chessground'
import type { Api as ChessgroundApi } from 'chessground/api'
import type { Color } from 'chessground/types'

const props = defineProps<{
  fen: string
  orientation?: Color
}>()

const boardEl = ref<HTMLElement | null>(null)
let cg: ChessgroundApi | null = null

onMounted(() => {
  if (!boardEl.value) return
  cg = Chessground(boardEl.value, {
    fen: props.fen,
    orientation: props.orientation ?? 'white',
    movable: { free: false, color: undefined },
    draggable: { enabled: false },
    selectable: { enabled: false },
  })
})

watch(
  () => props.fen,
  (newFen) => cg?.set({ fen: newFen }),
)

watch(
  () => props.orientation,
  (newOrientation) => cg?.set({ orientation: newOrientation ?? 'white' }),
)

onUnmounted(() => cg?.destroy())
</script>

<template>
  <div ref="boardEl" class="chess-board" />
</template>

<style scoped>
.chess-board {
  width: 100%;
  aspect-ratio: 1;
}
</style>
