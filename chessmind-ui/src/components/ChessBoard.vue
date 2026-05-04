<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { Chess } from 'chess.js'
import { Chessground } from 'chessground'
import type { Api as ChessgroundApi } from 'chessground/api'
import type { Color, Key } from 'chessground/types'

const props = defineProps<{
  fen: string
  orientation?: Color
  interactive?: boolean
}>()

const emit = defineEmits<{
  move: [uci: string]
}>()

const boardEl = ref<HTMLElement | null>(null)
let cg: ChessgroundApi | null = null

function legalDests(fen: string): Map<Key, Key[]> {
  const chess = new Chess(fen)
  const dests = new Map<Key, Key[]>()
  for (const move of chess.moves({ verbose: true })) {
    const srcs = dests.get(move.from as Key) ?? []
    srcs.push(move.to as Key)
    dests.set(move.from as Key, srcs)
  }
  return dests
}

function turnColor(fen: string): Color {
  return fen.split(' ')[1] === 'w' ? 'white' : 'black'
}

function handleMove(orig: Key, dest: Key): void {
  const chess = new Chess(props.fen)
  const piece = chess.get(orig as import('chess.js').Square)
  const isPromotion = piece?.type === 'p' && (dest[1] === '8' || dest[1] === '1')
  emit('move', isPromotion ? `${orig}${dest}q` : `${orig}${dest}`)
}

function movableConfig(fen: string, interactive: boolean) {
  if (!interactive) return { free: false as const, color: undefined }
  return {
    free: false as const,
    color: turnColor(fen),
    dests: legalDests(fen),
    events: { after: handleMove },
  }
}

onMounted(() => {
  if (!boardEl.value) return
  cg = Chessground(boardEl.value, {
    fen: props.fen,
    turnColor: turnColor(props.fen),
    orientation: props.orientation ?? 'white',
    movable: movableConfig(props.fen, props.interactive ?? false),
    draggable: { enabled: props.interactive ?? false },
    selectable: { enabled: props.interactive ?? false },
  })
})

// When the FEN changes (opponent's reply, reveal, etc.) we must update the
// board position AND recalculate the movable destinations for the new position.
watch(
  () => props.fen,
  (newFen) => {
    const interactive = props.interactive ?? false
    cg?.set({
      fen: newFen,
      turnColor: turnColor(newFen),
      movable: movableConfig(newFen, interactive),
      draggable: { enabled: interactive },
      selectable: { enabled: interactive },
    })
  },
)

// When only the interactive flag changes (e.g. mode transitions from 'guess'
// to 'analysis' after a correct guess) we must NOT touch the FEN. Calling
// cg.set({ fen: props.fen }) here would snap the piece back to its pre-drag
// position because props.fen still holds the FEN from before the user's move.
watch(
  () => props.interactive,
  (newInteractive) => {
    cg?.set({
      turnColor: turnColor(props.fen),
      movable: movableConfig(props.fen, newInteractive ?? false),
      draggable: { enabled: newInteractive ?? false },
      selectable: { enabled: newInteractive ?? false },
    })
  },
)

watch(
  () => props.orientation,
  (newOrientation) => cg?.set({ orientation: newOrientation ?? 'white' }),
)

onUnmounted(() => cg?.destroy())

function reset(): void {
  cg?.set({
    fen: props.fen,
    turnColor: turnColor(props.fen),
    movable: movableConfig(props.fen, props.interactive ?? false),
    draggable: { enabled: props.interactive ?? false },
    selectable: { enabled: props.interactive ?? false },
  })
}

defineExpose({ reset })
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
