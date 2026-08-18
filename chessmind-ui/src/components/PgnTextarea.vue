<script setup lang="ts">
import { ref, computed } from 'vue'
import { Chess } from 'chess.js'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'validation-change': [valid: boolean]
}>()

const touched = ref(false)

function validatePgn(pgn: string): boolean {
  if (!pgn.trim()) return false
  try {
    new Chess().loadPgn(pgn)
    return true
  } catch {
    return false
  }
}

const validationError = computed((): string | null => {
  if (!touched.value) return null
  const trimmed = props.modelValue.trim()
  if (!trimmed) return 'PGN is required'
  if (!validatePgn(props.modelValue)) return 'Invalid PGN — check your input and try again'
  return null
})

function handleInput(event: Event) {
  const value = (event.target as HTMLTextAreaElement).value
  touched.value = true
  emit('update:modelValue', value)
  emit('validation-change', validatePgn(value))
}
</script>

<template>
  <div class="pgn-textarea">
    <label for="pgn-input" class="pgn-label">Paste PGN</label>
    <textarea
      id="pgn-input"
      class="pgn-input"
      :class="{ 'pgn-input--error': validationError }"
      :value="modelValue"
      placeholder="[Event &quot;...&quot;]&#10;&#10;1. e4 e5 2. Nf3 Nc6 ..."
      rows="10"
      spellcheck="false"
      aria-describedby="pgn-error"
      @input="handleInput"
    />
    <p v-if="validationError" id="pgn-error" class="pgn-error" role="alert">
      {{ validationError }}
    </p>
  </div>
</template>

<style scoped>
.pgn-textarea {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.pgn-label {
  font-weight: 600;
  font-size: 0.9rem;
}

.pgn-input {
  width: 100%;
  padding: 0.75rem;
  font-family: monospace;
  font-size: 0.85rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  resize: vertical;
  box-sizing: border-box;
}

.pgn-input--error {
  border-color: #c0392b;
}

.pgn-error {
  color: #c0392b;
  font-size: 0.85rem;
  margin: 0;
}
</style>
