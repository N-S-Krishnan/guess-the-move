import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PgnTextarea from '@/components/PgnTextarea.vue'

const VALID_PGN = `[Event "Test"]
[White "Alice"]
[Black "Bob"]
[Result "*"]

1. e4 e5 2. Nf3 Nc6 *`

const INVALID_PGN = 'this is not pgn'

// setValue already dispatches the input event internally — no need for an explicit trigger.
async function typeInto(wrapper: ReturnType<typeof mount>, text: string) {
  const textarea = wrapper.find('textarea')
  await textarea.setValue(text)
}

describe('PgnTextarea', () => {
  it('renders a textarea element', () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('shows no error before the user has typed anything', () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows a required error when the field is cleared after being touched', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, 'some input')
    await typeInto(wrapper, '')
    expect(wrapper.find('[role="alert"]').text()).toContain('required')
  })

  it('shows an error for invalid PGN after input', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, INVALID_PGN)
    await wrapper.setProps({ modelValue: INVALID_PGN })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').text()).toContain('Invalid PGN')
  })

  it('shows no error for a valid PGN', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, VALID_PGN)
    await wrapper.setProps({ modelValue: VALID_PGN })
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('emits update:modelValue with the typed text', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, '1. e4')
    const emitted = wrapper.emitted('update:modelValue') as string[][]
    expect(emitted).toBeTruthy()
    expect(emitted[emitted.length - 1]).toEqual(['1. e4'])
  })

  it('emits validation-change with false for invalid PGN', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, INVALID_PGN)
    const emitted = wrapper.emitted('validation-change') as boolean[][]
    expect(emitted).toBeTruthy()
    const lastValue = emitted[emitted.length - 1]?.[0]
    expect(lastValue).toBe(false)
  })

  it('emits validation-change with true for a valid PGN', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, VALID_PGN)
    const emitted = wrapper.emitted('validation-change') as boolean[][]
    expect(emitted).toBeTruthy()
    const lastValue = emitted[emitted.length - 1]?.[0]
    expect(lastValue).toBe(true)
  })

  it('applies the error CSS class to the textarea when PGN is invalid', async () => {
    const wrapper = mount(PgnTextarea, { props: { modelValue: '' } })
    await typeInto(wrapper, INVALID_PGN)
    await wrapper.setProps({ modelValue: INVALID_PGN })
    expect(wrapper.find('textarea').classes()).toContain('pgn-input--error')
  })
})
