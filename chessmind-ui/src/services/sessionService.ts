import axios from 'axios'
import type { CreateSessionResponse } from '@/types/session'

const api = axios.create({ baseURL: '/api/v1' })

export async function createSession(pgn: string): Promise<CreateSessionResponse> {
  const { data } = await api.post<CreateSessionResponse>('/sessions', { pgn })
  return data
}
