import { onMounted, onUnmounted } from 'vue'
import { useSessionStore } from '@/stores/session'

export function useVariationKeyNav(): void {
  const store = useSessionStore()

  function handleKeydown(e: KeyboardEvent): void {
    if (store.mode !== 'analysis') return
    const target = e.target
    if (target instanceof HTMLTextAreaElement || target instanceof HTMLInputElement) return

    const ctrl = e.ctrlKey || e.metaKey

    if (ctrl && e.key === 'ArrowRight') {
      e.preventDefault()
      store.navigateToEnd()
    } else if (ctrl && e.key === 'ArrowLeft') {
      e.preventDefault()
      store.navigateToStart()
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      store.navigateForward()
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      store.navigateBackward()
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      store.navigateSiblingDown()
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      store.navigateSiblingUp()
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeydown)
  })
}
