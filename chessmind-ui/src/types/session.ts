export interface CreateSessionResponse {
  id: string
  white: string
  black: string
  plyCount: number
}

/** Shape passed as Vue Router location state when navigating to the setup screen. */
export interface SessionRouteState {
  white: string
  black: string
  plyCount: number
}

export type PlayerColor = 'white' | 'black'

export type SessionMode = 'guess' | 'reviewing' | 'analysis' | 'complete'

export interface SetupSessionRequest {
  playerToGuess: PlayerColor
  startMoveNumber: number
}

export interface SetupSessionResponse {
  fen: string
  moveNumber: number
  moves: string[]
}

export interface GuessResponse {
  correct: boolean
  correctMove: string | null
  nextFen: string | null
  fenAfterPlayer: string | null
}

export interface SkipResponse {
  nextFen: string | null
}

export interface VariationNode {
  id: string
  san: string
  uci: string
  fen: string
  fromFen: string
  symbol?: string
  comment?: string
  children: VariationNode[]
}

export interface AddAnalysisMoveResponse {
  id: string
  san: string
  fenAfter: string
}

export interface ResumeResponse {
  fen: string
  moveIndex: number
}
