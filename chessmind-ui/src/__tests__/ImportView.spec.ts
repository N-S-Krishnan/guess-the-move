import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import ImportView from '@/views/ImportView.vue'
import { useSessionStore } from '@/stores/session'

const VALID_PGN = `[Event "Test"]
[White "Alice"]
[Black "Bob"]
[Result "*"]

1. e4 e5 *`

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/import', name: 'import', component: ImportView },
      { path: '/setup/:id', name: 'setup', component: { template: '<div />' } },
    ],
  })
}

async function mountImportView() {
  const router = makeRouter()
  await router.push('/import')
  const wrapper = mount(ImportView, {
    global: {
      plugins: [router, createTestingPinia({ createSpy: vi.fn })],
    },
  })
  return { wrapper, router }
}

// setValue already dispatches the input event internally.
async function fillPgn(wrapper: ReturnType<typeof mount>, text: string) {
  await wrapper.find('textarea').setValue(text)
}

describe('ImportView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the PgnTextarea component', async () => {
    const { wrapper } = await mountImportView()
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('renders the submit button', async () => {
    const { wrapper } = await mountImportView()
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true)
  })

  it('submit button is disabled when PGN is empty', async () => {
    const { wrapper } = await mountImportView()
    expect((wrapper.find('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('submit button is disabled when PGN is invalid', async () => {
    const { wrapper } = await mountImportView()
    await fillPgn(wrapper, 'not valid pgn')
    expect((wrapper.find('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('submit button is enabled when PGN is valid', async () => {
    const { wrapper } = await mountImportView()
    await fillPgn(wrapper, VALID_PGN)
    expect((wrapper.find('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(false)
  })

  it('submit button is disabled while loading', async () => {
    const { wrapper } = await mountImportView()
    const store = useSessionStore()
    store.isLoading = true
    await fillPgn(wrapper, VALID_PGN)
    expect((wrapper.find('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('shows "Importing…" label while loading', async () => {
    const { wrapper } = await mountImportView()
    const store = useSessionStore()
    store.isLoading = true
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Importing')
  })

  it('displays the server error message when the store has an error', async () => {
    const { wrapper } = await mountImportView()
    const store = useSessionStore()
    store.serverError = 'PGN contains illegal moves'
    await wrapper.vm.$nextTick()
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('PGN contains illegal moves')
  })

  it('calls store.createSession with the PGN on submit', async () => {
    const { wrapper } = await mountImportView()
    const store = useSessionStore()
    vi.mocked(store.createSession).mockResolvedValue(null)

    await fillPgn(wrapper, VALID_PGN)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(store.createSession).toHaveBeenCalledWith(VALID_PGN)
  })

  it('navigates to the setup route on successful session creation', async () => {
    const { wrapper, router } = await mountImportView()
    const store = useSessionStore()
    vi.mocked(store.createSession).mockResolvedValue({
      id: 'abc-123',
      white: 'Alice',
      black: 'Bob',
      plyCount: 10,
    })

    await fillPgn(wrapper, VALID_PGN)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('setup')
    expect(router.currentRoute.value.params.id).toBe('abc-123')
  })
})
