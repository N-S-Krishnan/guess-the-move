import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { nextTick, defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useVariationKeyNav } from '@/composables/useVariationKeyNav'
import { useSessionStore } from '@/stores/session'

const TestComponent = defineComponent({
  setup() {
    useVariationKeyNav()
  },
  template: '<div><textarea data-testid="ta" /><input data-testid="inp" /></div>',
})

function mountWithMode(mode: string) {
  return mount(TestComponent, {
    attachTo: document.body,
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { session: { mode } },
        }),
      ],
    },
  })
}

async function fire(key: string, opts: { ctrlKey?: boolean; metaKey?: boolean } = {}) {
  document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, ...opts }))
  await nextTick()
}

describe('useVariationKeyNav', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('when mode is analysis', () => {
    let wrapper: ReturnType<typeof mount>
    let store: ReturnType<typeof useSessionStore>

    beforeEach(() => {
      wrapper = mountWithMode('analysis')
      store = useSessionStore()
    })

    afterEach(() => {
      wrapper.unmount()
    })

    it('ArrowRight calls navigateForward', async () => {
      await fire('ArrowRight')
      expect(store.navigateForward).toHaveBeenCalledOnce()
    })

    it('ArrowLeft calls navigateBackward', async () => {
      await fire('ArrowLeft')
      expect(store.navigateBackward).toHaveBeenCalledOnce()
    })

    it('ArrowDown calls navigateSiblingDown', async () => {
      await fire('ArrowDown')
      expect(store.navigateSiblingDown).toHaveBeenCalledOnce()
    })

    it('ArrowUp calls navigateSiblingUp', async () => {
      await fire('ArrowUp')
      expect(store.navigateSiblingUp).toHaveBeenCalledOnce()
    })

    it('Ctrl+ArrowRight calls navigateToEnd', async () => {
      await fire('ArrowRight', { ctrlKey: true })
      expect(store.navigateToEnd).toHaveBeenCalledOnce()
    })

    it('Ctrl+ArrowLeft calls navigateToStart', async () => {
      await fire('ArrowLeft', { ctrlKey: true })
      expect(store.navigateToStart).toHaveBeenCalledOnce()
    })

    it('Meta+ArrowRight calls navigateToEnd', async () => {
      await fire('ArrowRight', { metaKey: true })
      expect(store.navigateToEnd).toHaveBeenCalledOnce()
    })

    it('Ctrl+ArrowRight does not also call navigateForward', async () => {
      await fire('ArrowRight', { ctrlKey: true })
      expect(store.navigateForward).not.toHaveBeenCalled()
    })

    it('keydown inside a textarea is ignored', async () => {
      const ta = wrapper.find('[data-testid="ta"]').element
      ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
      await nextTick()
      expect(store.navigateForward).not.toHaveBeenCalled()
    })

    it('keydown inside an input is ignored', async () => {
      const inp = wrapper.find('[data-testid="inp"]').element
      inp.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
      await nextTick()
      expect(store.navigateForward).not.toHaveBeenCalled()
    })
  })

  describe('when mode is not analysis', () => {
    let wrapper: ReturnType<typeof mount>
    let store: ReturnType<typeof useSessionStore>

    beforeEach(() => {
      wrapper = mountWithMode('guess')
      store = useSessionStore()
    })

    afterEach(() => {
      wrapper.unmount()
    })

    it('ArrowRight does not call navigateForward', async () => {
      await fire('ArrowRight')
      expect(store.navigateForward).not.toHaveBeenCalled()
    })

    it('ArrowLeft does not call navigateBackward', async () => {
      await fire('ArrowLeft')
      expect(store.navigateBackward).not.toHaveBeenCalled()
    })

    it('Ctrl+ArrowRight does not call navigateToEnd', async () => {
      await fire('ArrowRight', { ctrlKey: true })
      expect(store.navigateToEnd).not.toHaveBeenCalled()
    })

    it('Ctrl+ArrowLeft does not call navigateToStart', async () => {
      await fire('ArrowLeft', { ctrlKey: true })
      expect(store.navigateToStart).not.toHaveBeenCalled()
    })

    it('ArrowDown does not call navigateSiblingDown', async () => {
      await fire('ArrowDown')
      expect(store.navigateSiblingDown).not.toHaveBeenCalled()
    })

    it('ArrowUp does not call navigateSiblingUp', async () => {
      await fire('ArrowUp')
      expect(store.navigateSiblingUp).not.toHaveBeenCalled()
    })
  })

  it('removes the keydown listener when the component is unmounted', async () => {
    const wrapper = mountWithMode('analysis')
    const store = useSessionStore()
    wrapper.unmount()
    await fire('ArrowRight')
    expect(store.navigateForward).not.toHaveBeenCalled()
  })
})
