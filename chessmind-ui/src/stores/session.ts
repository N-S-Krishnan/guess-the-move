import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import * as sessionService from '@/services/sessionService'
import type { CreateSessionResponse, PlayerColor, SetupSessionResponse } from '@/types/session'

function extractErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const detail: unknown = err.response?.data?.detail
    if (typeof detail === 'string') return detail
    return err.message
  }
  return 'An unexpected error occurred'
}

export const useSessionStore = defineStore('session', () => {
  const isLoading = ref(false)
  const serverError = ref<string | null>(null)

  // Populated by createSession; read by SetupView.
  const white = ref<string | null>(null)
  const black = ref<string | null>(null)
  const plyCount = ref<number | null>(null)

  // Study session state — populated after setup is confirmed
  const sessionId = ref<string | null>(null)
  const currentFen = ref<string | null>(null)
  const currentMoveNumber = ref<number | null>(null)
  const playerToGuess = ref<PlayerColor | null>(null)

  async function createSession(pgn: string): Promise<CreateSessionResponse | null> {
    isLoading.value = true
    serverError.value = null
    try {
      const result = await sessionService.createSession(pgn)
      white.value = result.white
      black.value = result.black
      plyCount.value = result.plyCount
      return result
    } catch (err) {
      serverError.value = extractErrorMessage(err)
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function setupSession(
    id: string,
    player: PlayerColor,
    startMoveNumber: number,
  ): Promise<SetupSessionResponse | null> {
    isLoading.value = true
    serverError.value = null
    try {
      const result = await sessionService.setupSession(id, {
        playerToGuess: player,
        startMoveNumber,
      })
      sessionId.value = id
      currentFen.value = result.fen
      currentMoveNumber.value = result.moveNumber
      playerToGuess.value = player
      return result
    } catch (err) {
      serverError.value = extractErrorMessage(err)
      return null
    } finally {
      isLoading.value = false
    }
  }

  function clearError() {
    serverError.value = null
  }

  return {
    isLoading,
    serverError,
    white,
    black,
    plyCount,
    sessionId,
    currentFen,
    currentMoveNumber,
    playerToGuess,
    createSession,
    setupSession,
    clearError,
  }
})
