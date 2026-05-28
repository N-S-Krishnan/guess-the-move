<script setup lang="ts">
import { useSessionStore } from '@/stores/session'
import ResumeBanner from '@/components/ResumeBanner.vue'
import CommentEditor from '@/components/CommentEditor.vue'
import MoveSymbolSelector from '@/components/MoveSymbolSelector.vue'
import VariationTree from '@/components/VariationTree.vue'

const store = useSessionStore()
</script>

<template>
  <div class="analysis-panel" data-testid="analysis-panel">
    <ResumeBanner />

    <div class="variation-tree-container">
      <span v-if="store.variationTree.length === 0" class="variation-hint">
        Make a move to explore a variation
      </span>
      <template v-else>
        <VariationTree
          v-for="root in store.variationTree"
          :key="root.id"
          :node="root"
        />
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

.variation-tree-container {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
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
