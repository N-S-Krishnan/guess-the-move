<script setup lang="ts">
import type { PlayerColor } from '@/types/session'

const props = defineProps<{
  white: string
  black: string
  modelValue: PlayerColor
}>()

const emit = defineEmits<{
  'update:modelValue': [value: PlayerColor]
}>()
</script>

<template>
  <div class="player-selector" role="group" aria-label="Select player to guess">
    <button
      type="button"
      class="player-btn player-btn--white"
      :class="{ 'player-btn--selected': props.modelValue === 'white' }"
      :aria-pressed="props.modelValue === 'white'"
      @click="emit('update:modelValue', 'white')"
    >
      <span class="player-piece">♔</span>
      <span class="player-name">{{ props.white }}</span>
      <span class="player-label">White</span>
    </button>

    <button
      type="button"
      class="player-btn player-btn--black"
      :class="{ 'player-btn--selected': props.modelValue === 'black' }"
      :aria-pressed="props.modelValue === 'black'"
      @click="emit('update:modelValue', 'black')"
    >
      <span class="player-piece">♚</span>
      <span class="player-name">{{ props.black }}</span>
      <span class="player-label">Black</span>
    </button>
  </div>
</template>

<style scoped>
.player-selector {
  display: flex;
  gap: 1rem;
}

.player-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 1.25rem 1rem;
  border: 2px solid #ddd;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.player-btn:hover {
  border-color: #aaa;
  background: #f0f0f0;
}

.player-btn--selected {
  border-color: #2c3e50;
  background: #eef2f7;
}

.player-piece {
  font-size: 2rem;
  line-height: 1;
}

.player-name {
  font-weight: 600;
  font-size: 0.95rem;
  text-align: center;
  word-break: break-word;
}

.player-label {
  font-size: 0.75rem;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
</style>
