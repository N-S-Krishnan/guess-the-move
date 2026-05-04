# ROADMAP

Phase 1 feature breakdown. Each feature is defined by its scope, the work required in each service layer, and its acceptance criteria.

---

## Feature 1 — PGN Import & Parsing

The user pastes a raw PGN string into the application. The system parses it, extracts metadata, and stores the game so that subsequent features can operate against a structured, validated move list.

### Scope

- A dedicated import screen with a PGN text input
- Client-side PGN format validation before submission
- Python service parses the PGN into a structured game representation
- Spring persists the session and returns enough data for the setup screen (Feature 2)

### `chessmind-analysis` (Python)

- Implement `POST /parse` endpoint
- Accept a raw PGN string in the request body
- Use `python-chess` to parse the game and extract:
  - Headers: `White`, `Black`, `Event`, `Date`, `Result`
  - Full mainline move list in both SAN and UCI notation
  - FEN after each move (including the starting position)
  - Total move count (ply and full-move number)
- Return a structured JSON response; reject malformed PGNs with a `400` and a human-readable error message
- Write `pytest` tests covering: valid single-game PGN, multi-game PGN (accept first game only), PGN with comments and annotations (strip them, parse cleanly), and invalid input

### `chessmind-api` (Spring / Kotlin)

- Implement `POST /api/v1/sessions`
- Accept `{ pgn: String }` in the request body
- Validate that the PGN field is not blank before forwarding
- Call `AnalysisClient.parse(pgn)` and handle errors from the Python service (propagate as `422`)
- Persist a new `StudySession` row with `pgn_raw`, `white`, `black`, `event`, and `status = 'pending_setup'`
- Cache the parsed move list and FEN array in Redis under `session:{id}:state` with a 24-hour TTL
- Return the session ID, player names, and total move count to the client
- Unit test the service layer with a mocked `AnalysisClient` and mocked repositories

### `chessmind-ui` (Vue 3)

- Build `ImportView.vue` with a `PgnTextarea.vue` child component
- Use `chess.js` on the client to perform lightweight format validation on input change; surface errors inline before the user submits
- On submit, call `sessionService.createSession(pgn)`
- On success, navigate to the setup route (Feature 2), passing the session ID, player names, and move count as route state
- On error, display the server's error message beneath the textarea
- Show a loading state while the request is in flight

### Acceptance Criteria

- [ ] Pasting a valid PGN and submitting creates a session and navigates to the setup screen
- [ ] The player names extracted from the PGN are available on the next screen
- [ ] Submitting an invalid PGN shows an error message and does not create a session
- [ ] A PGN containing comments (`{ }`) or variation markers (`( )`) is accepted and parsed correctly

---

## Feature 2 — Player & Start Move Selection

After a game is imported, the user selects which player's moves they want to guess and the move number from which the study session begins. Confirming this selection initializes the session into an active state.

### Scope

- A setup screen presenting player selection and a move number input
- Confirmation writes the setup choices to the session and transitions it to `in_progress`
- The session's initial board state is set to the FEN at the chosen start move

### `chessmind-analysis` (Python)

No new endpoints required. The FEN array returned by `/parse` in Feature 1 already contains the position at every move. The Spring service derives the starting FEN from the cached move list using the start move index.

### `chessmind-api` (Spring / Kotlin)

- Implement `PUT /api/v1/sessions/{id}/setup`
- Accept `{ playerToGuess: "white" | "black", startMoveNumber: Int }`
- Validate that `startMoveNumber` is within the bounds of the game's total move count
- Validate that `playerToGuess` is `"white"` or `"black"`
- Resolve the correct FEN from the cached move list at the given move index
- Update the `StudySession` row: set `player_to_guess`, `start_move_num`, `current_move_idx`, and `status = 'in_progress'`
- Update Redis state: set `currentFen`, `moveIndex`, and `mode = 'guess'`
- Return the starting FEN and the first move number to guess
- Reject requests for sessions not in `pending_setup` status with a `409`

### `chessmind-ui` (Vue 3)

- Build `SetupView.vue` (or render as a step within `ImportView`)
- Display a `PlayerSelector.vue` component: a two-option toggle showing the White and Black player names extracted in Feature 1
- Display a `StartMoveSlider.vue` component: a number input or slider bounded by move 1 and the game's last move
  - Default to move 1
  - Label the current position (e.g., "Starting from move 12 — Sicilian Defence, Najdorf Variation" if opening detection is available, otherwise just the move number)
- On confirm, call `sessionService.setupSession(sessionId, playerToGuess, startMoveNumber)`
- On success, navigate to `StudyView` and initialize the Pinia store with the returned FEN and move index

### Acceptance Criteria

- [ ] Both player names from the PGN headers appear as selectable options
- [ ] The move number input is bounded to the valid range for the loaded game
- [ ] Confirming the setup transitions the session to `in_progress` and navigates to the study board
- [ ] The chessboard renders the correct position for the chosen start move
- [ ] Attempting to set up a session that is already `in_progress` returns an error

---

## Feature 3 — Guess-the-Move Loop

The core study interaction. The user is shown the board at the current position and must drag or click to make the move they believe the chosen player played. The system evaluates the guess, provides feedback, plays the opponent's response if correct, and then re-enters guess mode for the next move.

### Scope

- The board is interactive in guess mode; the user makes a move by dragging a piece or clicking source and destination squares
- The guess is submitted to Spring, which delegates comparison to Python
- Incorrect guesses snap back with feedback; correct guesses animate the move, then automatically play the opponent's response
- After the opponent's reply, the board enters Analysis Mode (Feature 4 in PROPOSAL scope, but the transition point is defined here)
- The session tracks progress: which moves have been guessed, and whether each was correct on the first attempt

### `chessmind-analysis` (Python)

- Implement `POST /validate` endpoint
  - Accept `{ fen: String, uciMove: String }`
  - Return `{ legal: Boolean, san: String | null }`
  - Used by Spring to sanity-check that the submitted move is at minimum a legal move in the position before comparing it to the game move
- Implement `POST /compare` endpoint
  - Accept `{ fen: String, guessedUci: String, actualUci: String }`
  - Return `{ correct: Boolean, actualSan: String, actualUci: String, fenAfter: String }`
  - `fenAfter` is the FEN after the actual game move is applied (used regardless of whether the guess was correct, so the board can show the correct position after a reveal)
- Write tests for: correct guess, incorrect but legal guess, illegal move submitted, promotion moves, and castling

### `chessmind-api` (Spring / Kotlin)

- Implement `POST /api/v1/sessions/{id}/guess`
  - Accept `{ uciMove: String }`
  - Load the active session state from Redis (current FEN, move index, actual move at that index)
  - Call `AnalysisClient.validate(fen, uciMove)` — reject illegal moves with a `400` before proceeding
  - Call `AnalysisClient.compare(fen, guessedUci, actualUci)`
  - If correct:
    - Advance `moveIndex` by 1 (the guessed move)
    - Advance `moveIndex` by 1 again to skip the opponent's response (whose move and FEN are taken from the cached move list)
    - Update `currentFen` in Redis to the FEN after the opponent's reply
    - Set `mode = 'analysis'` in Redis (transition to free analysis)
    - Return `{ correct: true, actualSan, opponentSan, fenAfterOpponent }`
  - If incorrect:
    - Do not advance the move index
    - Return `{ correct: false, actualSan: null }` (do not reveal the correct move unless the client requests a reveal after N attempts — leave this as a client-side policy decision)
  - Persist a `GuessResult` record (move index, correct boolean, guessed UCI) to PostgreSQL for the post-game summary (Phase 2)
- Reject guess submissions when session `mode` is not `'guess'`

### `chessmind-ui` (Vue 3)

- In `StudyView.vue`, conditionally render `MoveGuessPanel.vue` when `store.mode === 'guess'`
- Wire the chessboard's `move` event to `store.submitGuess(uciMove)`
  - On incorrect: snap the piece back, display feedback in `GuessFeedback.vue`; the board must remain fully interactive so the user can immediately try another piece
  - On correct: animate the move on the board, then animate the opponent's reply after a short delay, then transition `store.mode` to `'analysis'`
- `GuessFeedback.vue` shows a "Give up" button from the **first** failed attempt (threshold: 1); it calls `store.revealMove()`, which advances past the current position without recording a correct guess
- `ChessBoard.vue`'s `reset()` function must restore the full movable/draggable/selectable config alongside the FEN so the board is interactive after snap-back (current bug: `reset()` only sets `fen`, leaving the board in the non-interactive state applied during the loading phase)
- The move list panel (`MoveList.vue`) shows all mainline moves up to and including the last completed guess, but masks all future moves with a placeholder so the user cannot read ahead

### Acceptance Criteria

- [x] Making the correct move advances the board, plays the opponent's reply, and transitions to analysis mode
- [x] Making an incorrect move snaps the piece back and shows a feedback message without advancing the position
- [x] The board accepts a second guess immediately after snapping back from an incorrect one
- [x] A "Give up" button is visible after the first wrong guess, not after a threshold of three
- [x] The move list does not reveal any moves beyond the current guess position
- [x] Illegal moves (e.g., moving into check) are rejected before being submitted to the API
- [x] After the opponent's reply is animated, the board is no longer interactive until "Next position →" is clicked (Feature 4)
- [x] A session that has no remaining moves to guess displays a completion state rather than prompting for another guess
