<script setup lang="ts">
const props = defineProps<{
  modelValue: number
  max: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

function handleChange(event: Event) {
  emit('update:modelValue', Number((event.target as HTMLInputElement).value))
}
</script>

<template>
  <div class="start-move-slider">
    <div class="slider-header">
      <label for="start-move-input" class="slider-label">Start from move</label>
      <span class="slider-value" aria-live="polite">{{ props.modelValue }}</span>
    </div>

    <input
      id="start-move-input"
      class="slider-range"
      type="range"
      :min="1"
      :max="props.max"
      :value="props.modelValue"
      @input="handleChange"
    />

    <div class="slider-bounds">
      <span>Move 1</span>
      <span>Move {{ props.max }}</span>
    </div>
  </div>
</template>

<style scoped>
.start-move-slider {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.slider-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.slider-label {
  font-weight: 600;
  font-size: 0.9rem;
}

.slider-value {
  font-size: 1.4rem;
  font-weight: 700;
  color: #2c3e50;
  min-width: 2.5rem;
  text-align: right;
}

.slider-range {
  width: 100%;
  accent-color: #2c3e50;
  cursor: pointer;
}

.slider-bounds {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: #888;
}
</style>