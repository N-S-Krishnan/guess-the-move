import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import * as sessionService from '@/services/sessionService'
import type { CreateSessionResponse, GuessResponse, PlayerColor, SessionMode, SetupSessionResponse, SkipResponse } from '@/types/session'

function extractErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const detail: unknown = err.response?.data?.detail
    if (typeof detail === 'string') return detail
    return err.message
  }
  return 'An unexpected error occurred'
}

function isSessionGone(err: unknown): boolean {
  return axios.isAxiosError(err) && err.response?.status === 404
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
  const mode = ref<SessionMode | null>(null)
  const moves = ref<string[] | null>(null)
  const lastGuessResult = ref<{ correct: boolean; correctMove: string | null } | null>(null)
  const isAnimating = ref(false)
  const lastGuessWasCorrect = ref(false)

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
      mode.value = 'guess'
      moves.value = result.moves
      return result
    } catch (err) {
      serverError.value = extractErrorMessage(err)
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function submitGuess(uci: string): Promise<GuessResponse | null> {
    if (!sessionId.value) return null
    isLoading.value = true
    serverError.value = null
    try {
      const result = await sessionService.submitGuess(sessionId.value, uci)
      if (result.correct) {
        lastGuessResult.value = null
        lastGuessWasCorrect.value = true
        // Disable board immediately — chessground already shows the player's move
        // from the user's drag, so we must NOT touch currentFen here or cg.set
        // will reset the board to the pre-drag position.
        isAnimating.value = true
        currentMoveNumber.value = (currentMoveNumber.value ?? 1) + 1
        if (result.nextFen) {
          const nextFen = result.nextFen
          setTimeout(() => {
            currentFen.value = nextFen   // FEN watcher animates the opponent's reply
            isAnimating.value = false
            mode.value = 'analysis'
            lastGuessWasCorrect.value = false
          }, 500)
        } else {
          setTimeout(() => {
            isAnimating.value = false
            mode.value = 'complete'
            lastGuessWasCorrect.value = false
          }, 300)
        }
      } else {
        lastGuessResult.value = { correct: false, correctMove: result.correctMove }
        lastGuessWasCorrect.value = false
        // Snap-back is handled by the caller (StudyView.handleMove) via boardRef.reset().
      }
      return result
    } catch (err) {
      if (isSessionGone(err)) {
        currentFen.value = null
        sessionId.value = null
      } else {
        serverError.value = extractErrorMessage(err)
      }
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function revealMove(): Promise<SkipResponse | null> {
    if (!sessionId.value) return null
    isLoading.value = true
    serverError.value = null
    try {
      const result = await sessionService.skipGuess(sessionId.value)
      lastGuessResult.value = null
      if (result.nextFen) {
        currentFen.value = result.nextFen
        currentMoveNumber.value = (currentMoveNumber.value ?? 1) + 1
        mode.value = 'analysis'
      } else {
        mode.value = 'complete'
      }
      return result
    } catch (err) {
      if (isSessionGone(err)) {
        currentFen.value = null
        sessionId.value = null
      } else {
        serverError.value = extractErrorMessage(err)
      }
      return null
    } finally {
      isLoading.value = false
    }
  }

  function nextPosition(): void {
    mode.value = 'guess'
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
    mode,
    moves,
    lastGuessResult,
    isAnimating,
    lastGuessWasCorrect,
    createSession,
    setupSession,
    submitGuess,
    revealMove,
    nextPosition,
    clearError,
  }
})
