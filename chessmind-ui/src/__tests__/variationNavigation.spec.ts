import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSessionStore } from '@/stores/session'
import type { VariationNode } from '@/types/session'

vi.mock('@/services/sessionService', () => ({
  createSession: vi.fn(),
  setupSession: vi.fn(),
  submitGuess: vi.fn(),
  skipGuess: vi.fn(),
  addAnalysisMove: vi.fn(),
  deleteLastAnalysisMove: vi.fn(),
  resumeStudy: vi.fn(),
  saveAnnotation: vi.fn(),
  listSessions: vi.fn(),
  getSession: vi.fn(),
  exportSession: vi.fn(),
}))

import * as sessionService from '@/services/sessionService'

function node(
  id: string,
  uci: string,
  san: string,
  children: VariationNode[] = [],
): VariationNode {
  return { id, san, uci, fen: `fen-${id}`, fromFen: null, children }
}

describe('variation navigation store actions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  function setup(tree: VariationNode[], path: VariationNode[] = []) {
    const store = useSessionStore()
    store.variationTree = tree
    store.currentPath = path
    return store
  }

  // ── navigateForward ──────────────────────────────────────────────────────────

  describe('navigateForward', () => {
    it('is a no-op when tree is empty and path is empty', () => {
      const store = setup([])
      store.navigateForward()
      expect(store.currentPath).toEqual([])
    })

    it('pushes variationTree[0] into path when path is empty', () => {
      const a = node('a', 'e2e4', 'e4')
      const store = setup([a])
      store.navigateForward()
      expect(store.currentPath).toEqual([a])
    })

    it('pushes first child of tip when path is non-empty', () => {
      const b = node('b', 'e7e5', 'e5')
      const a = node('a', 'e2e4', 'e4', [b])
      const store = setup([a], [a])
      store.navigateForward()
      expect(store.currentPath).toEqual([a, b])
    })

    it('is a no-op when the tip has no children', () => {
      const a = node('a', 'e2e4', 'e4')
      const store = setup([a], [a])
      store.navigateForward()
      expect(store.currentPath).toEqual([a])
    })
  })

  // ── navigateBackward ─────────────────────────────────────────────────────────

  describe('navigateBackward', () => {
    it('is a no-op when path is empty', () => {
      const store = setup([])
      store.navigateBackward()
      expect(store.currentPath).toEqual([])
    })

    it('pops the last node from path', () => {
      const a = node('a', 'e2e4', 'e4')
      const b = node('b', 'e7e5', 'e5')
      const store = setup([], [a, b])
      store.navigateBackward()
      expect(store.currentPath).toEqual([a])
    })

    it('leaves path empty after popping the only node', () => {
      const a = node('a', 'e2e4', 'e4')
      const store = setup([a], [a])
      store.navigateBackward()
      expect(store.currentPath).toEqual([])
    })
  })

  // ── navigateSiblingDown ──────────────────────────────────────────────────────

  describe('navigateSiblingDown', () => {
    it('is a no-op when path is empty', () => {
      const a = node('a', 'e2e4', 'e4')
      const store = setup([a])
      store.navigateSiblingDown()
      expect(store.currentPath).toEqual([])
    })

    it('is a no-op when there is only one sibling', () => {
      const a = node('a', 'e2e4', 'e4')
      const store = setup([a], [a])
      store.navigateSiblingDown()
      expect(store.currentPath).toEqual([a])
    })

    it('advances to the next sibling among top-level roots', () => {
      const a = node('a', 'e2e4', 'e4')
      const b = node('b', 'd2d4', 'd4')
      const store = setup([a, b], [a])
      store.navigateSiblingDown()
      expect(store.currentPath).toEqual([b])
    })

    it('wraps around to the first sibling when at the last', () => {
      const a = node('a', 'e2e4', 'e4')
      const b = node('b', 'd2d4', 'd4')
      const store = setup([a, b], [b])
      store.navigateSiblingDown()
      expect(store.currentPath).toEqual([a])
    })

    it('cycles among child siblings at a branch point', () => {
      const b = node('b', 'e7e5', 'e5')
      const c = node('c', 'd7d5', 'd5')
      const a = node('a', 'e2e4', 'e4', [b, c])
      const store = setup([a], [a, b])
      store.navigateSiblingDown()
      expect(store.currentPath).toEqual([a, c])
    })
  })

  // ── navigateSiblingUp ────────────────────────────────────────────────────────

  describe('navigateSiblingUp', () => {
    it('is a no-op when path is empty', () => {
      const store = setup([])
      store.navigateSiblingUp()
      expect(store.currentPath).toEqual([])
    })

    it('cycles in reverse among top-level roots', () => {
      const a = node('a', 'e2e4', 'e4')
      const b = node('b', 'd2d4', 'd4')
      const store = setup([a, b], [a])
      store.navigateSiblingUp()
      expect(store.currentPath).toEqual([b])
    })

    it('wraps around to the last sibling when at the first', () => {
      const a = node('a', 'e2e4', 'e4')
      const b = node('b', 'd2d4', 'd4')
      const store = setup([a, b], [b])
      store.navigateSiblingUp()
      expect(store.currentPath).toEqual([a])
    })
  })

  // ── navigateToEnd ────────────────────────────────────────────────────────────

  describe('navigateToEnd', () => {
    it('is a no-op when tree is empty and path is empty', () => {
      const store = setup([])
      store.navigateToEnd()
      expect(store.currentPath).toEqual([])
    })

    it('enters the tree from the root and follows children[0] to a leaf', () => {
      const c = node('c', 'g1f3', 'Nf3')
      const b = node('b', 'e7e5', 'e5', [c])
      const a = node('a', 'e2e4', 'e4', [b])
      const store = setup([a])
      store.navigateToEnd()
      expect(store.currentPath).toEqual([a, b, c])
    })

    it('continues from the current tip when path is non-empty', () => {
      const c = node('c', 'g1f3', 'Nf3')
      const b = node('b', 'e7e5', 'e5', [c])
      const a = node('a', 'e2e4', 'e4', [b])
      const store = setup([a], [a])
      store.navigateToEnd()
      expect(store.currentPath).toEqual([a, b, c])
    })

    it('is a no-op when already at a leaf', () => {
      const a = node('a', 'e2e4', 'e4')
      const store = setup([a], [a])
      store.navigateToEnd()
      expect(store.currentPath).toEqual([a])
    })
  })

  // ── navigateToStart ──────────────────────────────────────────────────────────

  describe('navigateToStart', () => {
    it('clears currentPath', () => {
      const a = node('a', 'e2e4', 'e4')
      const b = node('b', 'e7e5', 'e5')
      const store = setup([a], [a, b])
      store.navigateToStart()
      expect(store.currentPath).toEqual([])
    })

    it('is a no-op when path is already empty', () => {
      const store = setup([])
      store.navigateToStart()
      expect(store.currentPath).toEqual([])
    })
  })

  // ── addAnalysisMove deduplication ────────────────────────────────────────────

  describe('addAnalysisMove deduplication', () => {
    it('navigates into an existing child without calling the service when UCI matches', async () => {
      const existing = node('child', 'e2e4', 'e4')
      const parent = node('parent', 'd2d4', 'd4', [existing])
      const store = setup([parent], [parent])
      store.sessionId = 'sess-1'
      store.mode = 'analysis'

      await store.addAnalysisMove('e2e4')

      expect(store.currentPath).toEqual([parent, existing])
      expect(sessionService.addAnalysisMove).not.toHaveBeenCalled()
    })

    it('navigates into a root-level node when path is empty and UCI matches', async () => {
      const existing = node('root', 'e2e4', 'e4')
      const store = setup([existing], [])
      store.sessionId = 'sess-1'
      store.mode = 'analysis'

      await store.addAnalysisMove('e2e4')

      expect(store.currentPath).toEqual([existing])
      expect(sessionService.addAnalysisMove).not.toHaveBeenCalled()
    })
  })
})
