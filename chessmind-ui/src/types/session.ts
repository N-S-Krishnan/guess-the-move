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

export interface SetupSessionRequest {
  playerToGuess: PlayerColor
  startMoveNumber: number
}

export interface SetupSessionResponse {
  fen: string
  moveNumber: number
}
