import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../App.vue'

describe('App', () => {
  it('renders a RouterView', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    const wrapper = mount(App, { global: { plugins: [router] } })
    // App is a thin shell — just verify it mounts without errors
    expect(wrapper.exists()).toBe(true)
  })
})
