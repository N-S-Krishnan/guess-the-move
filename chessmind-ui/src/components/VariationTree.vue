<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useSessionStore } from '@/stores/session'
import type { VariationNode } from '@/types/session'
import VariationTree from './VariationTree.vue'

const props = defineProps<{ node: VariationNode }>()

const store = useSessionStore()
const chipRef = ref<HTMLButtonElement | null>(null)

const isActive = computed(() => store.currentPath[store.currentPath.length - 1]?.id === props.node.id)
const mainChild = computed(() => props.node.children[0] ?? null)
const sideChildren = computed(() => props.node.children.slice(1))

watch(isActive, (active) => {
  if (active) nextTick(() => chipRef.value?.scrollIntoView({ block: 'nearest' }))
})
</script>

<template>
  <span class="vt-node">
    <button
      ref="chipRef"
      type="button"
      class="variation-move"
      :class="{ 'variation-move--active': isActive }"
      @click="store.jumpToNode(node)"
    >
      {{ node.san }}
    </button>

    <span v-if="isActive && node.children.length > 1" class="branch-choices">
      <button
        v-for="c in node.children"
        :key="c.id"
        type="button"
        class="branch-chip"
        @click.stop="store.jumpToNode(c)"
      >
        → {{ c.san }}
      </button>
    </span>

    <VariationTree v-if="mainChild" :node="mainChild" />

    <div v-for="child in sideChildren" :key="child.id" class="sub-variation">
      <VariationTree :node="child" />
    </div>
  </span>
</template>

<style scoped>
.vt-node {
  display: contents;
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

.branch-choices {
  display: inline-flex;
  gap: 0.25rem;
  margin-left: 0.5rem;
}

.branch-chip {
  padding: 0.1rem 0.35rem;
  background: none;
  border: 1px solid #ced4da;
  border-radius: 3px;
  cursor: pointer;
  color: #6c757d;
  font-family: monospace;
  font-size: 0.8rem;
  line-height: 1.4;
}

.branch-chip:hover {
  background: #f1f3f5;
  border-color: #adb5bd;
  color: #2c3e50;
}

.sub-variation {
  width: 100%;
  padding-left: 1rem;
  border-left: 2px solid #dee2e6;
  margin: 0.25rem 0;
  background: #f8f9fa;
  border-radius: 0 4px 4px 0;
}
</style>
