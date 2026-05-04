import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import MoveList from '@/components/MoveList.vue'

// 6 SANs = 3 full moves: 1.e4 e5  2.Nf3 Nc6  3.Bb5 a6
const SAMPLE_MOVES = ['e4', 'e5', 'Nf3', 'Nc6', 'Bb5', 'a6']

// 5 SANs = 3 full moves where the game ends on White's move (odd ply count)
const ODD_MOVES = ['e4', 'e5', 'Nf3', 'Nc6', 'Bb5']

function mountList(session: {
  moves?: string[] | null
  currentMoveNumber?: number | null
  mode?: string | null
} = {}) {
  return mount(MoveList, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            session: {
              moves: SAMPLE_MOVES,
              currentMoveNumber: 1,
              mode: 'guess',
              ...session,
            },
          },
        }),
      ],
    },
  })
}

describe('MoveList', () => {
  // ── Empty / no moves ────────────────────────────────────────────────────────

  it('renders nothing when moves is null', () => {
    const wrapper = mountList({ moves: null })
    expect(wrapper.find('[data-testid="move-list"]').exists()).toBe(false)
  })

  it('renders nothing when moves is an empty array', () => {
    const wrapper = mountList({ moves: [] })
    expect(wrapper.find('[data-testid="move-list"]').exists()).toBe(false)
  })

  // ── Row count ────────────────────────────────────────────────────────────────

  it('renders one row per full move', () => {
    const wrapper = mountList()
    expect(wrapper.findAll('.move-row')).toHaveLength(3)
  })

  it('renders the correct move numbers', () => {
    const wrapper = mountList()
    ;[1, 2, 3].forEach((n) => {
      expect(wrapper.find(`[data-testid="move-row-${n}"]`).exists()).toBe(true)
    })
  })

  // ── All moves masked at the start ────────────────────────────────────────────

  it('masks all moves when currentMoveNumber is 1 and no guess has been made', () => {
    const wrapper = mountList({ currentMoveNumber: 1 })
    ;[1, 2, 3].forEach((n) => {
      expect(wrapper.find(`[data-testid="white-san-${n}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="white-mask-${n}"]`).exists()).toBe(true)
    })
  })

  // ── Partial reveal after correct guesses ────────────────────────────────────

  it('reveals move 1 and masks moves 2+ after the first correct guess', () => {
    const wrapper = mountList({ currentMoveNumber: 2 })

    // Move 1 revealed
    expect(wrapper.find('[data-testid="white-san-1"]').text()).toBe('e4')
    expect(wrapper.find('[data-testid="black-san-1"]').text()).toBe('e5')

    // Moves 2 and 3 masked
    expect(wrapper.find('[data-testid="white-san-2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="white-mask-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="white-san-3"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="white-mask-3"]').exists()).toBe(true)
  })

  it('reveals moves 1–2 and masks move 3 when currentMoveNumber is 3', () => {
    const wrapper = mountList({ currentMoveNumber: 3 })

    expect(wrapper.find('[data-testid="white-san-1"]').text()).toBe('e4')
    expect(wrapper.find('[data-testid="black-san-1"]').text()).toBe('e5')
    expect(wrapper.find('[data-testid="white-san-2"]').text()).toBe('Nf3')
    expect(wrapper.find('[data-testid="black-san-2"]').text()).toBe('Nc6')

    expect(wrapper.find('[data-testid="white-san-3"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="white-mask-3"]').exists()).toBe(true)
  })

  it('shows the correct SAN text for each revealed move', () => {
    const wrapper = mountList({ currentMoveNumber: 4 })

    expect(wrapper.find('[data-testid="white-san-1"]').text()).toBe('e4')
    expect(wrapper.find('[data-testid="black-san-1"]').text()).toBe('e5')
    expect(wrapper.find('[data-testid="white-san-2"]').text()).toBe('Nf3')
    expect(wrapper.find('[data-testid="black-san-2"]').text()).toBe('Nc6')
    expect(wrapper.find('[data-testid="white-san-3"]').text()).toBe('Bb5')
    expect(wrapper.find('[data-testid="black-san-3"]').text()).toBe('a6')
  })

  // ── Placeholder text ─────────────────────────────────────────────────────────

  it('renders "…" as the placeholder text for masked moves', () => {
    const wrapper = mountList({ currentMoveNumber: 1 })
    expect(wrapper.find('[data-testid="white-mask-1"]').text()).toBe('…')
    expect(wrapper.find('[data-testid="black-mask-1"]').text()).toBe('…')
  })

  // ── Complete mode — all revealed ─────────────────────────────────────────────

  it('reveals all moves when mode is complete', () => {
    const wrapper = mountList({ mode: 'complete', currentMoveNumber: 1 })
    ;[1, 2, 3].forEach((n) => {
      expect(wrapper.find(`[data-testid="white-san-${n}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-testid="black-san-${n}"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-testid="white-mask-${n}"]`).exists()).toBe(false)
    })
  })

  // ── Odd ply count (game ends on White's move) ────────────────────────────────

  it('does not render a black column for the last row when the game ends on White', () => {
    const wrapper = mountList({ moves: ODD_MOVES, currentMoveNumber: 4 })

    // Move 3 has White's SAN but no Black SAN
    expect(wrapper.find('[data-testid="white-san-3"]').text()).toBe('Bb5')
    expect(wrapper.find('[data-testid="black-san-3"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="black-mask-3"]').exists()).toBe(false)
  })

  it('masks the odd last move correctly', () => {
    const wrapper = mountList({ moves: ODD_MOVES, currentMoveNumber: 1 })

    expect(wrapper.find('[data-testid="white-mask-3"]').exists()).toBe(true)
    // No black entry at all — there is no Black move in this final full-move slot
    expect(wrapper.find('[data-testid="black-san-3"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="black-mask-3"]').exists()).toBe(false)
  })
})
