<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import * as sessionService from '@/services/sessionService'
import type { SessionSummary } from '@/types/session'

const router = useRouter()
const store = useSessionStore()

const sessions = ref<SessionSummary[]>([])
const isLoading = ref(false)
const loadError = ref<string | null>(null)
const resumingId = ref<string | null>(null)

onMounted(async () => {
  isLoading.value = true
  try {
    sessions.value = await sessionService.listSessions()
  } catch {
    loadError.value = 'Failed to load sessions. Please try again.'
  } finally {
    isLoading.value = false
  }
})

async function resume(id: string): Promise<void> {
  resumingId.value = id
  try {
    const loaded = await sessionService.getSession(id)
    await store.loadSession(loaded)
    router.push({ name: 'study', params: { id } })
  } catch {
    loadError.value = `Failed to load session. Please try again.`
  } finally {
    resumingId.value = null
  }
}

function formatPgnDate(raw: string | null): string {
  if (!raw) return ''
  // PGN date format: YYYY.MM.DD — drop any segment that contains '?' (unknown)
  const parts = raw.split('.').filter((p) => !p.includes('?'))
  return parts.join('-')
}

function progressLabel(session: SessionSummary): string {
  if (session.currentMoveIdx == null || session.plyCount == null) return ''
  const guessed = Math.floor(session.currentMoveIdx / 2)
  const total = Math.floor(session.plyCount / 2)
  return `${guessed} / ${total} moves`
}

function statusLabel(status: SessionSummary['status']): string {
  if (status === 'pending_setup') return 'Not started'
  if (status === 'in_progress') return 'In progress'
  return 'Completed'
}
</script>

<template>
  <main class="session-list-view">
    <header class="list-header">
      <h1 class="list-title">My Games</h1>
      <RouterLink :to="{ name: 'import' }" class="new-game-btn">+ New Game</RouterLink>
    </header>

    <div v-if="loadError" class="error-banner" data-testid="load-error">
      {{ loadError }}
    </div>

    <div v-if="isLoading" class="loading" data-testid="loading">
      Loading sessions…
    </div>

    <template v-else-if="sessions.length > 0">
      <ul class="session-list" data-testid="session-list">
        <li
          v-for="session in sessions"
          :key="session.id"
          class="session-card"
          :data-testid="`session-card-${session.id}`"
        >
          <div class="card-meta">
            <span class="card-players">{{ session.white }} vs {{ session.black }}</span>
            <span class="card-event">{{ session.event }}</span>
            <span v-if="session.site && session.site !== '?'" class="card-detail">{{ session.site }}</span>
            <span v-if="formatPgnDate(session.date)" class="card-detail">Game: {{ formatPgnDate(session.date) }}</span>
            <span class="card-detail">Imported: {{ session.createdAt.slice(0, 10) }}</span>
          </div>

          <div class="card-status-row">
            <span
              class="status-badge"
              :class="`status-${session.status}`"
            >{{ statusLabel(session.status) }}</span>
            <span v-if="session.status === 'in_progress'" class="card-progress">
              {{ progressLabel(session) }}
            </span>
          </div>

          <div class="card-actions">
            <button
              v-if="session.status === 'pending_setup'"
              type="button"
              class="action-btn"
              @click="router.push({ name: 'setup', params: { id: session.id } })"
            >
              Setup →
            </button>
            <button
              v-else
              type="button"
              class="action-btn"
              :disabled="resumingId === session.id"
              @click="resume(session.id)"
            >
              {{ resumingId === session.id ? 'Loading…' : 'Resume →' }}
            </button>
          </div>
        </li>
      </ul>
    </template>

    <div v-else-if="!isLoading" class="empty-state" data-testid="empty-state">
      <p>No games yet.</p>
      <RouterLink :to="{ name: 'import' }" class="new-game-btn">Import a Game</RouterLink>
    </div>
  </main>
</template>

<style scoped>
.session-list-view {
  max-width: 640px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}

.list-title {
  margin: 0;
  font-size: 1.6rem;
  color: #2c3e50;
}

.new-game-btn {
  padding: 0.45rem 1.1rem;
  font-size: 0.95rem;
  font-weight: 600;
  background: #2c3e50;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  text-decoration: none;
}

.new-game-btn:hover {
  background: #3d5166;
}

.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.session-card {
  padding: 1rem 1.25rem;
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.card-players {
  font-weight: 600;
  color: #2c3e50;
}

.card-event {
  font-size: 0.85rem;
  color: #6c757d;
}

.card-detail {
  font-size: 0.82rem;
  color: #868e96;
}

.card-status-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.status-badge {
  font-size: 0.78rem;
  font-weight: 600;
  padding: 0.2rem 0.55rem;
  border-radius: 20px;
}

.status-pending_setup {
  background: #e9ecef;
  color: #495057;
}

.status-in_progress {
  background: #d4edda;
  color: #155724;
}

.status-completed {
  background: #cce5ff;
  color: #004085;
}

.card-progress {
  font-size: 0.85rem;
  color: #6c757d;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
}

.action-btn {
  padding: 0.4rem 1rem;
  font-size: 0.9rem;
  font-weight: 600;
  background: #f0f4f8;
  color: #2c3e50;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}

.action-btn:hover:not(:disabled) {
  background: #e2e8f0;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error-banner {
  padding: 0.75rem 1rem;
  background: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  color: #721c24;
  margin-bottom: 1rem;
}

.loading,
.empty-state {
  text-align: center;
  color: #6c757d;
  padding: 3rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}
</style>
