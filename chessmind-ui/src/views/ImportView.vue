<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import PgnTextarea from '@/components/PgnTextarea.vue'

const router = useRouter()
const sessionStore = useSessionStore()

const pgn = ref('')
const isPgnValid = ref(false)

const canSubmit = computed(() => isPgnValid.value && !sessionStore.isLoading)

watch(pgn, () => {
  if (sessionStore.serverError) sessionStore.clearError()
})

async function handleSubmit() {
  if (!canSubmit.value) return

  const result = await sessionStore.createSession(pgn.value)
  if (result) {
    await router.push({
      name: 'setup',
      params: { id: result.id },
      state: { white: result.white, black: result.black, plyCount: result.plyCount },
    })
  }
}
</script>

<template>
  <main class="import-view">
    <h1 class="import-title">Import a Game</h1>
    <p class="import-subtitle">Paste a PGN to start a guess-the-move study session.</p>

    <form class="import-form" @submit.prevent="handleSubmit">
      <PgnTextarea v-model="pgn" @validation-change="isPgnValid = $event" />

      <p v-if="sessionStore.serverError" class="server-error" role="alert">
        {{ sessionStore.serverError }}
      </p>

      <button type="submit" class="submit-btn" :disabled="!canSubmit">
        <span v-if="sessionStore.isLoading" class="loading-text">Importing…</span>
        <span v-else>Start Guessing</span>
      </button>
    </form>
  </main>
</template>

<style scoped>
.import-view {
  max-width: 680px;
  margin: 4rem auto;
  padding: 0 1.5rem;
}

.import-title {
  font-size: 1.75rem;
  margin-bottom: 0.25rem;
}

.import-subtitle {
  color: #666;
  margin-bottom: 2rem;
}

.import-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.server-error {
  color: #c0392b;
  font-size: 0.9rem;
  margin: 0;
  padding: 0.75rem;
  background: #fdf0ef;
  border-left: 3px solid #c0392b;
  border-radius: 2px;
}

.submit-btn {
  align-self: flex-start;
  padding: 0.65rem 1.5rem;
  font-size: 1rem;
  background: #2c3e50;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
