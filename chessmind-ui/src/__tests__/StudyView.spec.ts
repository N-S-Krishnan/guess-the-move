import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import StudyView from '@/views/StudyView.vue'
import { useSessionStore } from '@/stores/session'

// ChessBoard wraps chessground which needs a real browser layout.
// Replace it with a lightweight stub in tests.
vi.mock('@/components/ChessBoard.vue', () => ({
  default: {
    name: 'ChessBoard',
    props: ['fen', 'orientation', 'interactive'],
    emits: ['move'],
    template:
      '<div data-testid="chess-board" :data-fen="fen" :data-orientation="orientation" :data-interactive="String(interactive)" />',
  },
}))

vi.mock('@/components/MoveGuessPanel.vue', () => ({
  default: {
    name: 'MoveGuessPanel',
    template: '<div data-testid="move-guess-panel" />',
  },
}))

vi.mock('@/components/MoveList.vue', () => ({
  default: {
    name: 'MoveList',
    template: '<div data-testid="move-list-stub" />',
  },
}))

vi.mock('@/components/StudyComplete.vue', () => ({
  default: {
    name: 'StudyComplete',
    template: '<div data-testid="study-complete-stub" />',
  },
}))

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/import', name: 'import', component: { template: '<div />' } },
      { path: '/study/:id', name: 'study', component: StudyView },
    ],
  })
}

async function mountStudyView(
  storeOverrides: {
    currentFen?: string | null
    playerToGuess?: string | null
    currentMoveNumber?: number | null
    mode?: string | null
    sessionId?: string | null
    isLoading?: boolean
    isAnimating?: boolean
  } = {},
) {
  const router = makeRouter()
  await router.push('/study/sess-1')
  const wrapper = mount(StudyView, {
    global: {
      plugins: [
        router,
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            session: {
              currentFen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
              playerToGuess: 'white',
              currentMoveNumber: 1,
              mode: null,
              sessionId: 'sess-1',
              isLoading: false,
              isAnimating: false,
              ...storeOverrides,
            },
          },
        }),
      ],
    },
  })
  return { wrapper, router }
}

describe('StudyView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the ChessBoard with the FEN from the store', async () => {
    const fen = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1'
    const { wrapper } = await mountStudyView({ currentFen: fen })
    const board = wrapper.find('[data-testid="chess-board"]')
    expect(board.exists()).toBe(true)
    expect(board.attributes('data-fen')).toBe(fen)
  })

  it('passes white orientation when playerToGuess is white', async () => {
    const { wrapper } = await mountStudyView({ playerToGuess: 'white' })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-orientation')).toBe('white')
  })

  it('passes black orientation when playerToGuess is black', async () => {
    const { wrapper } = await mountStudyView({ playerToGuess: 'black' })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-orientation')).toBe('black')
  })

  it('displays the player label', async () => {
    const { wrapper } = await mountStudyView({ playerToGuess: 'black' })
    expect(wrapper.text()).toContain('Black')
  })

  it('displays the starting move number', async () => {
    const { wrapper } = await mountStudyView({ currentMoveNumber: 5 })
    expect(wrapper.text()).toContain('5')
  })

  it('redirects to import when there is no session state', async () => {
    const { router } = await mountStudyView({ currentFen: null })
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('import')
  })

  it('renders MoveGuessPanel when mode is guess', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    expect(wrapper.find('[data-testid="move-guess-panel"]').exists()).toBe(true)
  })

  it('does not render MoveGuessPanel when mode is analysis', async () => {
    const { wrapper } = await mountStudyView({ mode: 'analysis' })
    expect(wrapper.find('[data-testid="move-guess-panel"]').exists()).toBe(false)
  })

  it('does not render MoveGuessPanel when mode is null', async () => {
    const { wrapper } = await mountStudyView({ mode: null })
    expect(wrapper.find('[data-testid="move-guess-panel"]').exists()).toBe(false)
  })

  it('does not render MoveGuessPanel when mode is complete', async () => {
    const { wrapper } = await mountStudyView({ mode: 'complete' })
    expect(wrapper.find('[data-testid="move-guess-panel"]').exists()).toBe(false)
  })

  it('passes interactive=true to ChessBoard when mode is guess', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-interactive')).toBe('true')
  })

  it('passes interactive=false to ChessBoard when mode is analysis', async () => {
    const { wrapper } = await mountStudyView({ mode: 'analysis' })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-interactive')).toBe('false')
  })

  it('passes interactive=false to ChessBoard when mode is null', async () => {
    const { wrapper } = await mountStudyView({ mode: null })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-interactive')).toBe('false')
  })

  it('passes interactive=false when isLoading is true even in guess mode', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess', isLoading: true })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-interactive')).toBe('false')
  })

  it('passes interactive=false when isAnimating is true even in guess mode', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess', isAnimating: true })
    expect(wrapper.find('[data-testid="chess-board"]').attributes('data-interactive')).toBe('false')
  })

  it('calls store.submitGuess with the UCI when ChessBoard emits move', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    const store = useSessionStore()
    await wrapper.findComponent({ name: 'ChessBoard' }).vm.$emit('move', 'e2e4')
    expect(store.submitGuess).toHaveBeenCalledWith('e2e4')
  })

  it('calls store.submitGuess with the correct UCI for a different move', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    const store = useSessionStore()
    await wrapper.findComponent({ name: 'ChessBoard' }).vm.$emit('move', 'd2d4')
    expect(store.submitGuess).toHaveBeenCalledWith('d2d4')
  })

  it('always renders MoveList regardless of mode', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    expect(wrapper.find('[data-testid="move-list-stub"]').exists()).toBe(true)
  })

  // ── Completion state (criterion 6) ────────────────────────────────────────

  it('renders StudyComplete when mode is complete', async () => {
    const { wrapper } = await mountStudyView({ mode: 'complete' })
    expect(wrapper.find('[data-testid="study-complete-stub"]').exists()).toBe(true)
  })

  it('does not render StudyComplete when mode is guess', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    expect(wrapper.find('[data-testid="study-complete-stub"]').exists()).toBe(false)
  })

  it('does not render StudyComplete when mode is analysis', async () => {
    const { wrapper } = await mountStudyView({ mode: 'analysis' })
    expect(wrapper.find('[data-testid="study-complete-stub"]').exists()).toBe(false)
  })

  it('does not render MoveGuessPanel when mode is complete', async () => {
    const { wrapper } = await mountStudyView({ mode: 'complete' })
    expect(wrapper.find('[data-testid="move-guess-panel"]').exists()).toBe(false)
  })

  it('hides the info aside when mode is complete', async () => {
    const { wrapper } = await mountStudyView({ mode: 'complete' })
    expect(wrapper.find('.study-info').exists()).toBe(false)
  })

  it('shows the info aside when mode is guess', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    expect(wrapper.find('.study-info').exists()).toBe(true)
  })

  // ── Error feedback (server error display) ─────────────────────────────────

  it('shows the server error banner when serverError is set', async () => {
    const { wrapper } = await mountStudyView()
    const store = useSessionStore()
    store.serverError = 'Analysis service unavailable'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="server-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="server-error"]').text()).toContain('Analysis service unavailable')
  })

  it('does not show the server error banner when serverError is null', async () => {
    const { wrapper } = await mountStudyView()
    expect(wrapper.find('[data-testid="server-error"]').exists()).toBe(false)
  })

  it('calls store.clearError when the error banner is clicked', async () => {
    const { wrapper } = await mountStudyView()
    const store = useSessionStore()
    store.serverError = 'Some error'
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="server-error"]').trigger('click')
    expect(store.clearError).toHaveBeenCalledOnce()
  })

  // ── Analysis panel / Next position button ─────────────────────────────────

  it('renders the analysis panel when mode is analysis', async () => {
    const { wrapper } = await mountStudyView({ mode: 'analysis' })
    expect(wrapper.find('[data-testid="analysis-panel"]').exists()).toBe(true)
  })

  it('does not render the analysis panel when mode is guess', async () => {
    const { wrapper } = await mountStudyView({ mode: 'guess' })
    expect(wrapper.find('[data-testid="analysis-panel"]').exists()).toBe(false)
  })

  it('does not render the analysis panel when mode is complete', async () => {
    const { wrapper } = await mountStudyView({ mode: 'complete' })
    expect(wrapper.find('[data-testid="analysis-panel"]').exists()).toBe(false)
  })

  it('does not render the analysis panel when mode is null', async () => {
    const { wrapper } = await mountStudyView({ mode: null })
    expect(wrapper.find('[data-testid="analysis-panel"]').exists()).toBe(false)
  })

  it('calls store.nextPosition when the Next position button is clicked', async () => {
    const { wrapper } = await mountStudyView({ mode: 'analysis' })
    const store = useSessionStore()
    await wrapper.find('[data-testid="next-btn"]').trigger('click')
    expect(store.nextPosition).toHaveBeenCalledOnce()
  })

  // ── Session-gone redirect (404) ────────────────────────────────────────────

  it('redirects to import when currentFen is cleared after a session-gone error', async () => {
    const { wrapper, router } = await mountStudyView()
    const store = useSessionStore()

    store.currentFen = null
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('import')
  })
})
