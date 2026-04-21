<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import PlayerSelector from '@/components/PlayerSelector.vue'
import StartMoveSlider from '@/components/StartMoveSlider.vue'
import type { PlayerColor } from '@/types/session'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()

const sessionId = route.params.id as string

// Player names and ply count are stored in the session store by createSession.
const white = computed(() => sessionStore.white ?? 'White')
const black = computed(() => sessionStore.black ?? 'Black')

// Last full-move number: each full move has 2 plies (white + black)
const lastMoveNumber = computed(() => Math.ceil((sessionStore.plyCount ?? 2) / 2))

const playerToGuess = ref<PlayerColor>('white')
const startMoveNumber = ref(1)

async function handleConfirm() {
  const result = await sessionStore.setupSession(
    sessionId,
    playerToGuess.value,
    startMoveNumber.value,
  )
  if (result) {
    await router.push({ name: 'study', params: { id: sessionId } })
  }
}
</script>

<template>
  <main class="setup-view">
    <h1 class="setup-title">Set Up Your Session</h1>
    <p class="setup-subtitle">Choose which player's moves you want to guess and where to start.</p>

    <div class="setup-form">
      <section class="setup-section">
        <h2 class="section-heading">Guess as</h2>
        <PlayerSelector
          :white="white"
          :black="black"
          v-model="playerToGuess"
        />
      </section>

      <section class="setup-section">
        <h2 class="section-heading">Starting move</h2>
        <StartMoveSlider v-model="startMoveNumber" :max="lastMoveNumber" />
      </section>

      <p v-if="sessionStore.serverError" class="server-error" role="alert">
        {{ sessionStore.serverError }}
      </p>

      <button
        type="button"
        class="confirm-btn"
        data-testid="confirm-btn"
        :disabled="sessionStore.isLoading"
        @click="handleConfirm"
      >
        <span v-if="sessionStore.isLoading" class="loading-text">Starting…</span>
        <span v-else>Start Guessing</span>
      </button>
    </div>
  </main>
</template>

<style scoped>
.setup-view {
  max-width: 560px;
  margin: 4rem auto;
  padding: 0 1.5rem;
}

.setup-title {
  font-size: 1.75rem;
  margin-bottom: 0.25rem;
}

.setup-subtitle {
  color: #666;
  margin-bottom: 2.5rem;
}

.setup-form {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.setup-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.section-heading {
  font-size: 0.85rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #555;
  margin: 0;
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

.confirm-btn {
  align-self: flex-start;
  padding: 0.65rem 1.5rem;
  font-size: 1rem;
  background: #2c3e50;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.confirm-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
