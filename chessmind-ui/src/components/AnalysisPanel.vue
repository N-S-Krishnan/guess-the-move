<script setup lang="ts">
import { useSessionStore } from '@/stores/session'
import ResumeBanner from '@/components/ResumeBanner.vue'
import CommentEditor from '@/components/CommentEditor.vue'
import MoveSymbolSelector from '@/components/MoveSymbolSelector.vue'
import type { VariationNode } from '@/types/session'

const store = useSessionStore()

function handleNodeClick(node: VariationNode) {
  store.jumpToNode(node)
}
</script>

<template>
  <div class="analysis-panel" data-testid="analysis-panel">
    <ResumeBanner />

    <div class="variation-line" data-testid="variation-line">
      <span v-if="store.currentPath.length === 0" class="variation-hint">
        Make a move to explore a variation
      </span>
      <template v-else>
        <button
          v-for="(node, i) in store.currentPath"
          :key="node.id"
          type="button"
          class="variation-move"
          :class="{ 'variation-move--active': i === store.currentPath.length - 1 }"
          :data-testid="`variation-move-${i}`"
          @click="handleNodeClick(node)"
        >
          {{ node.san }}
        </button>
      </template>
    </div>

    <MoveSymbolSelector />

    <CommentEditor />

    <div class="analysis-controls">
      <button
        type="button"
        class="takeback-btn"
        :disabled="store.currentPath.length === 0 || store.isLoading"
        @click="store.takeBackAnalysisMove()"
      >
        ↩ Take back
      </button>
    </div>
  </div>
</template>

<style scoped>
.analysis-panel {
  width: min(560px, 100%);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.variation-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem;
  min-height: 2.25rem;
  padding: 0.5rem 0.75rem;
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  font-family: monospace;
  font-size: 0.9rem;
}

.variation-hint {
  color: #adb5bd;
  font-style: italic;
  font-family: sans-serif;
  font-size: 0.875rem;
}

.variation-move {
  padding: 0.15rem 0.45rem;
  background: none;
  border: 1px solid transparent;
  border-radius: 3px;
  cursor: pointer;
  color: #2c3e50;
  font-family: monospace;
  font-size: 0.9rem;
  line-height: 1.4;
}

.variation-move:hover {
  background: #f1f3f5;
  border-color: #ced4da;
}

.variation-move--active {
  background: #2c3e50;
  color: #fff;
  border-color: #2c3e50;
}

.variation-move--active:hover {
  background: #3d5166;
  border-color: #3d5166;
}

.analysis-controls {
  display: flex;
  gap: 0.75rem;
  padding: 0.25rem 0;
}

.takeback-btn {
  padding: 0.4rem 0.9rem;
  font-size: 0.9rem;
  background: #fff;
  border: 1px solid #ced4da;
  border-radius: 4px;
  cursor: pointer;
  color: #495057;
}

.takeback-btn:hover:not(:disabled) {
  background: #f8f9fa;
  border-color: #adb5bd;
}

.takeback-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
