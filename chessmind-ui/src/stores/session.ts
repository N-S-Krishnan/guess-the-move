import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import * as sessionService from '@/services/sessionService'
import type { CreateSessionResponse } from '@/types/session'

function extractErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const detail: unknown = err.response?.data?.detail
    if (typeof detail === 'string') return detail
    return err.message
  }
  return 'An unexpected error occurred'
}

export const useSessionStore = defineStore('session', () => {
  const isLoading = ref(false)
  const serverError = ref<string | null>(null)

  async function createSession(pgn: string): Promise<CreateSessionResponse | null> {
    isLoading.value = true
    serverError.value = null
    try {
      return await sessionService.createSession(pgn)
    } catch (err) {
      serverError.value = extractErrorMessage(err)
      return null
    } finally {
      isLoading.value = false
    }
  }

  return { isLoading, serverError, createSession }
})
