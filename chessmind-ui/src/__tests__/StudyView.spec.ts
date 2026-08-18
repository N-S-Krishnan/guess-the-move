import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import StudyView from '@/views/StudyView.vue'

// ChessBoard wraps chessground which needs a real browser layout.
// Replace it with a lightweight stub in tests.
vi.mock('@/components/ChessBoard.vue', () => ({
  default: {
    name: 'ChessBoard',
    props: ['fen', 'orientation'],
    template: '<div data-testid="chess-board" :data-fen="fen" :data-orientation="orientation" />',
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
  storeOverrides: { currentFen?: string | null; playerToGuess?: string | null; currentMoveNumber?: number | null } = {},
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
})
