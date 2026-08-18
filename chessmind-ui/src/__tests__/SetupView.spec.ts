import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import SetupView from '@/views/SetupView.vue'
import { useSessionStore } from '@/stores/session'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/setup/:id', name: 'setup', component: SetupView },
      { path: '/study/:id', name: 'study', component: { template: '<div />' } },
    ],
  })
}

interface SessionState {
  white?: string
  black?: string
  plyCount?: number
  isLoading?: boolean
  serverError?: string | null
}

async function mountSetupView(sessionState: SessionState = {}) {
  const router = makeRouter()
  await router.push('/setup/sess-1')
  const wrapper = mount(SetupView, {
    global: {
      plugins: [
        router,
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            session: {
              white: 'Alice',
              black: 'Bob',
              plyCount: 14,
              ...sessionState,
            },
          },
        }),
      ],
    },
  })
  return { wrapper, router }
}

describe('SetupView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders both player names from the session store', async () => {
    const { wrapper } = await mountSetupView({ white: 'Magnus', black: 'Fabiano' })
    expect(wrapper.text()).toContain('Magnus')
    expect(wrapper.text()).toContain('Fabiano')
  })

  it('renders the StartMoveSlider bounded to the correct max', async () => {
    // plyCount 14 → 7 full moves → slider max should be 7
    const { wrapper } = await mountSetupView({ plyCount: 14 })
    const range = wrapper.find('input[type="range"]')
    expect(range.exists()).toBe(true)
    expect((range.element as HTMLInputElement).max).toBe('7')
  })

  it('slider max rounds up for an odd ply count', async () => {
    // plyCount 13 → ceil(13/2) = 7
    const { wrapper } = await mountSetupView({ plyCount: 13 })
    const range = wrapper.find('input[type="range"]')
    expect((range.element as HTMLInputElement).max).toBe('7')
  })

  it('confirm button is disabled while the store is loading', async () => {
    const { wrapper } = await mountSetupView({ isLoading: true })
    const btn = wrapper.find('[data-testid="confirm-btn"]')
    expect((btn.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('shows "Starting…" label while loading', async () => {
    const { wrapper } = await mountSetupView({ isLoading: true })
    expect(wrapper.text()).toContain('Starting')
  })

  it('displays the server error when the store has one', async () => {
    const { wrapper } = await mountSetupView({ serverError: 'Session is already in progress' })
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Session is already in progress')
  })

  it('calls store.setupSession with sessionId, playerToGuess, and startMoveNumber on confirm', async () => {
    const { wrapper } = await mountSetupView()
    const store = useSessionStore()
    vi.mocked(store.setupSession).mockResolvedValue(null)

    await wrapper.find('[data-testid="confirm-btn"]').trigger('click')
    await flushPromises()

    expect(store.setupSession).toHaveBeenCalledWith('sess-1', 'white', 1)
  })

  it('navigates to study route on successful setup', async () => {
    const { wrapper, router } = await mountSetupView()
    const store = useSessionStore()
    vi.mocked(store.setupSession).mockResolvedValue({ fen: 'startfen', moveNumber: 1 })

    await wrapper.find('[data-testid="confirm-btn"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('study')
    expect(router.currentRoute.value.params.id).toBe('sess-1')
  })

  it('does not navigate when setupSession returns null (error case)', async () => {
    const { wrapper, router } = await mountSetupView()
    const store = useSessionStore()
    vi.mocked(store.setupSession).mockResolvedValue(null)

    await wrapper.find('[data-testid="confirm-btn"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('setup')
  })
})
