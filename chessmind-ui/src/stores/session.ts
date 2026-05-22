import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import * as sessionService from '@/services/sessionService'
import type { CreateSessionResponse, GuessResponse, LoadSessionResponse, PlayerColor, ResumeResponse, SessionMode, SetupSessionResponse, SkipResponse, VariationNode } from '@/types/session'

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

function findPath(nodes: VariationNode[], targetFen: string): VariationNode[] | null {
  for (const node of nodes) {
    if (node.fen === targetFen) return [node]
    const childPath = findPath(node.children, targetFen)
    if (childPath) return [node, ...childPath]
  }
  return null
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

  // Variation tree — accumulates analysis branches across all guess positions in the session
  const variationTree = ref<VariationNode[]>([])
  // Stack of nodes representing the current navigation path within the tree
  const currentPath = ref<VariationNode[]>([])
  // FEN of the board position currently shown in analysis mode
  const activeAnalysisFen = computed(
    () => currentPath.value[currentPath.value.length - 1]?.fen ?? currentFen.value,
  )

  // Annotation state for the current analysis position
  const currentComment = ref<string>('')
  const currentSymbol = ref<string | null>(null)

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
            mode.value = 'reviewing'
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

  async function addAnalysisMove(uci: string): Promise<void> {
    if (!sessionId.value) return
    isLoading.value = true
    serverError.value = null
    const fromFen = activeAnalysisFen.value ?? ''
    try {
      const result = await sessionService.addAnalysisMove(sessionId.value, uci, fromFen)
      const node: VariationNode = {
        id: result.id,
        san: result.san,
        uci,
        fen: result.fenAfter,
        fromFen,
        children: [],
      }
      const tip = currentPath.value[currentPath.value.length - 1]
      if (tip === undefined) {
        variationTree.value.push(node)
      } else {
        tip.children.push(node)
      }
      currentPath.value.push(node)
      currentComment.value = ''
      currentSymbol.value = null
    } catch (err) {
      serverError.value = extractErrorMessage(err)
    } finally {
      isLoading.value = false
    }
  }

  async function takeBackAnalysisMove(): Promise<void> {
    if (!sessionId.value || currentPath.value.length === 0) return
    isLoading.value = true
    serverError.value = null
    try {
      await sessionService.deleteLastAnalysisMove(sessionId.value)
      const removed = currentPath.value[currentPath.value.length - 1]
      currentPath.value.pop()
      const parent = currentPath.value[currentPath.value.length - 1]
      if (parent === undefined) {
        variationTree.value = variationTree.value.filter((n) => n !== removed)
      } else {
        parent.children = parent.children.filter((n) => n !== removed)
      }
    } catch (err) {
      serverError.value = extractErrorMessage(err)
    } finally {
      isLoading.value = false
    }
  }

  function jumpToNode(node: VariationNode): void {
    const path = findPath(variationTree.value, node.fen)
    if (path) {
      currentPath.value = path
      currentComment.value = ''
      currentSymbol.value = null
    }
  }

  function nextPosition(): void {
    currentPath.value = []
    currentComment.value = ''
    currentSymbol.value = null
    mode.value = 'analysis'
  }

  async function resumeStudy(): Promise<ResumeResponse | null> {
    if (!sessionId.value) return null
    isLoading.value = true
    serverError.value = null
    try {
      const result = await sessionService.resumeStudy(sessionId.value)
      currentFen.value = result.fen
      currentPath.value = []
      currentComment.value = ''
      currentSymbol.value = null
      mode.value = 'guess'
      return result
    } catch (err) {
      serverError.value = extractErrorMessage(err)
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function saveComment(): Promise<void> {
    if (!sessionId.value) return
    const fen = activeAnalysisFen.value ?? ''
    if (!fen) return
    serverError.value = null
    isLoading.value = true
    try {
      await sessionService.saveAnnotation(sessionId.value, fen, { comment: currentComment.value })
    } catch (err) {
      serverError.value = extractErrorMessage(err)
    } finally {
      isLoading.value = false
    }
  }

  async function toggleSymbol(symbol: string): Promise<void> {
    if (!sessionId.value) return
    const fen = activeAnalysisFen.value ?? ''
    if (!fen) return
    const newSymbol = currentSymbol.value === symbol ? '' : symbol
    serverError.value = null
    isLoading.value = true
    try {
      await sessionService.saveAnnotation(sessionId.value, fen, { symbol: newSymbol })
      currentSymbol.value = newSymbol === '' ? null : newSymbol
    } catch (err) {
      serverError.value = extractErrorMessage(err)
    } finally {
      isLoading.value = false
    }
  }

  async function loadSession(response: LoadSessionResponse): Promise<void> {
    sessionId.value = response.id
    white.value = response.white
    black.value = response.black
    playerToGuess.value = (response.playerToGuess as PlayerColor) ?? null
    currentMoveNumber.value =
      response.currentMoveIdx != null
        ? Math.floor(response.currentMoveIdx / 2) + 1
        : response.startMoveNum
    currentFen.value = response.currentFen
    mode.value = (response.mode as SessionMode) ?? null
    moves.value = response.moves
    plyCount.value = response.plyCount
    variationTree.value = []
    currentPath.value = []
    currentComment.value = ''
    currentSymbol.value = null
    lastGuessResult.value = null
    isAnimating.value = false
    serverError.value = null
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
    variationTree,
    currentPath,
    activeAnalysisFen,
    currentComment,
    currentSymbol,
    createSession,
    setupSession,
    submitGuess,
    revealMove,
    addAnalysisMove,
    takeBackAnalysisMove,
    jumpToNode,
    nextPosition,
    loadSession,
    resumeStudy,
    saveComment,
    toggleSymbol,
    clearError,
  }
})
