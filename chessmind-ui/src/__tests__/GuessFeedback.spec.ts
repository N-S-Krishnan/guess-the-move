import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick } from 'vue'
import GuessFeedback from '@/components/GuessFeedback.vue'
import { useSessionStore } from '@/stores/session'

type LastGuessResult = { correct: boolean; correctMove: string | null } | null

function mountFeedback({ lastGuessResult = null as LastGuessResult } = {}) {
  return mount(GuessFeedback, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { session: { lastGuessResult } },
        }),
      ],
    },
  })
}

describe('GuessFeedback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── No wrong guess yet ────────────────────────────────────────────────────

  it('renders nothing when lastGuessResult is null', () => {
    const wrapper = mountFeedback({ lastGuessResult: null })
    expect(wrapper.find('[data-testid="guess-feedback"]').exists()).toBe(false)
  })

  // ── First wrong guess ─────────────────────────────────────────────────────

  it('shows the feedback element after the first wrong guess', () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    expect(wrapper.find('[data-testid="guess-feedback"]').exists()).toBe(true)
  })

  it('displays the "Not the move" message', () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    expect(wrapper.find('[data-testid="feedback-message"]').text()).toContain('Not the move')
  })

  it('shows the Give up button immediately after the first wrong guess', () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    expect(wrapper.find('[data-testid="reveal-btn"]').exists()).toBe(true)
  })

  it('Give up button label reads "Give up"', () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    expect(wrapper.find('[data-testid="reveal-btn"]').text()).toBe('Give up')
  })

  // ── Attempt counter ───────────────────────────────────────────────────────

  it('shows attempt 1 of 3 after the first wrong guess', () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    expect(wrapper.find('[data-testid="attempt-count"]').text()).toBe('Attempt 1 of 3')
  })

  it('increments the attempt counter on each wrong guess', async () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    const store = useSessionStore()

    store.lastGuessResult = { correct: false, correctMove: 'd2d4' }
    await nextTick()
    expect(wrapper.find('[data-testid="attempt-count"]').text()).toBe('Attempt 2 of 3')
  })

  // ── Auto-reveal at max attempts ───────────────────────────────────────────

  it('calls store.revealMove automatically on the third wrong guess', async () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    const store = useSessionStore()

    store.lastGuessResult = { correct: false, correctMove: 'd2d4' }
    await nextTick()
    expect(store.revealMove).not.toHaveBeenCalled()

    store.lastGuessResult = { correct: false, correctMove: 'c2c4' }
    await nextTick()
    expect(store.revealMove).toHaveBeenCalledOnce()
  })

  it('does not auto-reveal before reaching the maximum attempts', async () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    const store = useSessionStore()

    store.lastGuessResult = { correct: false, correctMove: 'd2d4' }
    await nextTick()
    expect(store.revealMove).not.toHaveBeenCalled()
  })

  // ── Reveal button action ──────────────────────────────────────────────────

  it('calls store.revealMove when the Give up button is clicked', async () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    const store = useSessionStore()

    await wrapper.find('[data-testid="reveal-btn"]').trigger('click')

    expect(store.revealMove).toHaveBeenCalledOnce()
  })

  // ── Correct guess / reset ─────────────────────────────────────────────────

  it('hides the feedback when lastGuessResult is cleared after a correct guess', async () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    const store = useSessionStore()

    expect(wrapper.find('[data-testid="guess-feedback"]').exists()).toBe(true)

    store.lastGuessResult = null
    await nextTick()

    expect(wrapper.find('[data-testid="guess-feedback"]').exists()).toBe(false)
  })

  it('resets attempts to zero when lastGuessResult is cleared', async () => {
    const wrapper = mountFeedback({ lastGuessResult: { correct: false, correctMove: 'e2e4' } })
    const store = useSessionStore()
    expect(wrapper.find('[data-testid="reveal-btn"]').exists()).toBe(true)

    store.lastGuessResult = null
    await nextTick()
    expect(wrapper.find('[data-testid="guess-feedback"]').exists()).toBe(false)
  })
})
