import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import StudyComplete from '@/components/StudyComplete.vue'

// 6 SANs = 3 full moves: white plays 3, black plays 3
const EVEN_MOVES = ['e4', 'e5', 'Nf3', 'Nc6', 'Bb5', 'a6']
// 5 SANs = white plays 3, black plays 2
const ODD_MOVES = ['e4', 'e5', 'Nf3', 'Nc6', 'Bb5']

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/import', name: 'import', component: { template: '<div />' } },
      { path: '/study/:id', name: 'study', component: { template: '<div />' } },
    ],
  })
}

function mountComplete(session: {
  playerToGuess?: string | null
  moves?: string[] | null
} = {}) {
  const router = makeRouter()
  return {
    wrapper: mount(StudyComplete, {
      global: {
        plugins: [
          router,
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              session: {
                playerToGuess: 'white',
                moves: EVEN_MOVES,
                ...session,
              },
            },
          }),
        ],
      },
    }),
    router,
  }
}

describe('StudyComplete', () => {
  it('renders the completion element', () => {
    const { wrapper } = mountComplete()
    expect(wrapper.find('[data-testid="study-complete"]').exists()).toBe(true)
  })

  it('displays the completion heading', () => {
    const { wrapper } = mountComplete()
    expect(wrapper.find('[data-testid="complete-heading"]').text()).toContain('Study complete')
  })

  it('shows White in the detail when playerToGuess is white', () => {
    const { wrapper } = mountComplete({ playerToGuess: 'white' })
    expect(wrapper.find('[data-testid="complete-detail"]').text()).toContain('White')
  })

  it('shows Black in the detail when playerToGuess is black', () => {
    const { wrapper } = mountComplete({ playerToGuess: 'black' })
    expect(wrapper.find('[data-testid="complete-detail"]').text()).toContain('Black')
  })

  it('counts white moves correctly for an even ply count', () => {
    // 6 plies → 3 white moves
    const { wrapper } = mountComplete({ playerToGuess: 'white', moves: EVEN_MOVES })
    expect(wrapper.find('[data-testid="complete-detail"]').text()).toContain('3')
  })

  it('counts black moves correctly for an even ply count', () => {
    // 6 plies → 3 black moves
    const { wrapper } = mountComplete({ playerToGuess: 'black', moves: EVEN_MOVES })
    expect(wrapper.find('[data-testid="complete-detail"]').text()).toContain('3')
  })

  it('counts white moves correctly for an odd ply count', () => {
    // 5 plies → white played 3, black played 2
    const { wrapper } = mountComplete({ playerToGuess: 'white', moves: ODD_MOVES })
    expect(wrapper.find('[data-testid="complete-detail"]').text()).toContain('3')
  })

  it('counts black moves correctly for an odd ply count', () => {
    // 5 plies → black played 2
    const { wrapper } = mountComplete({ playerToGuess: 'black', moves: ODD_MOVES })
    expect(wrapper.find('[data-testid="complete-detail"]').text()).toContain('2')
  })

  it('renders the study-another button', () => {
    const { wrapper } = mountComplete()
    expect(wrapper.find('[data-testid="study-another-btn"]').exists()).toBe(true)
  })

  it('navigates to import when study-another is clicked', async () => {
    const { wrapper, router } = mountComplete()
    await router.push('/study/sess-1')
    await wrapper.find('[data-testid="study-another-btn"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('import')
  })
})
