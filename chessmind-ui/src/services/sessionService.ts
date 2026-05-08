import axios from 'axios'
import type {
  AddAnalysisMoveResponse,
  CreateSessionResponse,
  GuessResponse,
  SetupSessionRequest,
  SetupSessionResponse,
  SkipResponse,
} from '@/types/session'

const api = axios.create({ baseURL: '/api/v1' })

export async function createSession(pgn: string): Promise<CreateSessionResponse> {
  const { data } = await api.post<CreateSessionResponse>('/sessions', { pgn })
  return data
}

export async function setupSession(
  sessionId: string,
  body: SetupSessionRequest,
): Promise<SetupSessionResponse> {
  const { data } = await api.put<SetupSessionResponse>(`/sessions/${sessionId}/setup`, body)
  return data
}

export async function submitGuess(sessionId: string, uci: string): Promise<GuessResponse> {
  const { data } = await api.post<GuessResponse>(`/sessions/${sessionId}/guess`, { move: uci })
  return data
}

export async function skipGuess(sessionId: string): Promise<SkipResponse> {
  const { data } = await api.post<SkipResponse>(`/sessions/${sessionId}/skip`)
  return data
}

export async function addAnalysisMove(
  sessionId: string,
  uciMove: string,
  fromFen: string,
): Promise<AddAnalysisMoveResponse> {
  const { data } = await api.post<AddAnalysisMoveResponse>(
    `/sessions/${sessionId}/analysis/move`,
    { uciMove, fromFen },
  )
  return data
}

export async function deleteLastAnalysisMove(sessionId: string): Promise<void> {
  await api.delete(`/sessions/${sessionId}/analysis/move`)
}
