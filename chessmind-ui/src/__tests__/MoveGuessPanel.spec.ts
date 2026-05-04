import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import MoveGuessPanel from '@/components/MoveGuessPanel.vue'

vi.mock('@/components/GuessFeedback.vue', () => ({
  default: {
    name: 'GuessFeedback',
    template: '<div data-testid="guess-feedback-stub" />',
  },
}))

type LastGuessResult = { correct: boolean; correctMove: string | null } | null

function mountPanel(sessionState: {
  playerToGuess?: string | null
  currentMoveNumber?: number | null
  lastGuessResult?: LastGuessResult
  lastGuessWasCorrect?: boolean
} = {}) {
  return mount(MoveGuessPanel, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            session: {
              playerToGuess: 'white',
              currentMoveNumber: 1,
              lastGuessResult: null,
              lastGuessWasCorrect: false,
              ...sessionState,
            },
          },
        }),
      ],
    },
  })
}

describe('MoveGuessPanel', () => {
  it('renders the panel element', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="move-guess-panel"]').exists()).toBe(true)
  })

  it('displays the current move number', () => {
    const wrapper = mountPanel({ currentMoveNumber: 7 })
    expect(wrapper.text()).toContain('7')
  })

  it('displays White when playerToGuess is white', () => {
    const wrapper = mountPanel({ playerToGuess: 'white' })
    expect(wrapper.text()).toContain('White')
  })

  it('displays Black when playerToGuess is black', () => {
    const wrapper = mountPanel({ playerToGuess: 'black' })
    expect(wrapper.text()).toContain('Black')
  })

  it('does not render GuessFeedback when there is no wrong guess', () => {
    const wrapper = mountPanel({ lastGuessResult: null })
    expect(wrapper.find('[data-testid="guess-feedback-stub"]').exists()).toBe(false)
  })

  it('renders GuessFeedback after a wrong guess', () => {
    const wrapper = mountPanel({
      lastGuessResult: { correct: false, correctMove: 'e2e4' },
    })
    expect(wrapper.find('[data-testid="guess-feedback-stub"]').exists()).toBe(true)
  })

  it('shows the correct banner when lastGuessWasCorrect is true', () => {
    const wrapper = mountPanel({ lastGuessWasCorrect: true })
    expect(wrapper.find('[data-testid="correct-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="correct-banner"]').text()).toContain('Correct!')
  })

  it('hides the guess prompt and feedback when correct banner is shown', () => {
    const wrapper = mountPanel({ lastGuessWasCorrect: true })
    expect(wrapper.find('[data-testid="guess-prompt"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="guess-feedback-stub"]').exists()).toBe(false)
  })

  it('shows the guess prompt when lastGuessWasCorrect is false', () => {
    const wrapper = mountPanel({ lastGuessWasCorrect: false })
    expect(wrapper.find('[data-testid="correct-banner"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="guess-prompt"]').exists()).toBe(true)
  })
})
