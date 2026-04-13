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
