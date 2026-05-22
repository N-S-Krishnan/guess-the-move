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

---

## Feature 4 — Free Analysis Mode & Resume

After the current guess position is resolved — either by a correct guess or by giving up — the user may freely explore the position: making speculative moves, building variation lines, adding comments, and annotating moves with symbols before resuming the study at the next guess prompt.

### Scope

- The board enters Analysis Mode via two paths: clicking "Next position →" after a correct guess and the opponent's reply, or immediately and automatically after the give-up / reveal path (`store.revealMove()`) from Feature 3
- The board is fully interactive: any legal move is accepted and added to the variation tree
- A take-back button removes the last analysis move
- A `CommentEditor` allows free-text annotation per position, auto-saved on blur
- The user can apply a move annotation symbol (!, ?, !!, ??, !?, ?!) to any move in the active variation
- A sticky `ResumeBanner` is always visible; clicking "Resume Study" returns to Guess Mode at the next mainline position
- All analysis moves, comments, and symbols are persisted to PostgreSQL in the `annotations` table

### `chessmind-analysis` (Python)

No new endpoints are required. The existing `POST /validate` endpoint (Feature 3) is reused by Spring to confirm that each analysis move is legal in the current position before appending it to the variation tree.

### `chessmind-api` (Spring / Kotlin)

- Implement `POST /api/v1/sessions/{id}/analysis/move`
  - Accept `{ uciMove: String, fromFen: String }`
  - Call `AnalysisClient.validate(fen, uciMove)`; reject illegal moves with `400`
  - Append a new `annotations` row as a child of the current position node (set `parent_id` to the annotation for `fromFen`, or null if branching from the mainline)
  - Update the `variationStack` in Redis to reflect the new active position
  - Return `{ san: String, fenAfter: String }`
- Implement `DELETE /api/v1/sessions/{id}/analysis/move`
  - Pop the last entry from the Redis `variationStack`
  - Return the restored `{ fen: String }` so the board can revert
- Implement `POST /api/v1/sessions/{id}/resume`
  - Clear the Redis `variationStack`
  - Set `mode = 'guess'` in Redis
  - Set `currentFen` to the FEN at the current `moveIndex` (the next mainline position to guess)
  - Return `{ fen: String, moveIndex: Int }` so the client can re-initialize the board
- Implement `PUT /api/v1/sessions/{id}/annotation`
  - Accept `{ fen: String, comment: String?, symbol: String? }` (both fields optional; at least one must be present)
  - Upsert `comment` and/or `symbol` on the matching `annotations` row for this session and FEN
  - Validate `symbol` is one of `!`, `?`, `!!`, `??`, `!?`, `?!` if present; reject with `400` otherwise
  - Return `204 No Content`
- Reject `POST /analysis/move` and `DELETE /analysis/move` when session `mode` is not `'analysis'` (`409`)
- Unit test: service layer with mocked `AnalysisClient` and repositories for each of the four endpoints above

### `chessmind-ui` (Vue 3)

- In `StudyView.vue`, conditionally render `AnalysisPanel.vue` when `store.mode === 'analysis'`
- `AnalysisPanel.vue` contains:
  - `MoveList.vue` — displays the mainline up to the current guess position plus any active variation moves in the `variationStack`; clicking a variation node calls `store.jumpToVariation(fen)` (navigates the board without a server round-trip, using the FEN from the node)
  - `CommentEditor.vue` — a plain `<textarea>` bound to `store.currentComment`; on blur, calls `sessionService.saveAnnotation(sessionId, fen, { comment })` if the value has changed
  - `MoveSymbolSelector.vue` — a row of six symbol buttons (!, ?, !!, ??, !?, ?!) rendered inline after each move entry in `MoveList`; clicking one calls `sessionService.saveAnnotation(sessionId, fen, { symbol })` immediately and toggles off if clicked again
  - `ResumeBanner.vue` — a sticky bar rendered above the board; "Resume Study" button calls `store.resumeStudy()`, which hits `POST /resume` and transitions `store.mode` back to `'guess'`
- Wire the board's `move` event in analysis mode to `store.addAnalysisMove(uciMove)`
- Add a "↩ Take back" button that calls `store.takeBackAnalysisMove()`; disable it when `store.variationStack` is empty
- `store.resumeStudy()` must reset the board's FEN and restore full movable/draggable config (same concern as the Feature 3 snap-back bug — call the board's full reset helper, not just `setFen`)

### Acceptance Criteria

- [ ] Clicking "Next position →" after a correct guess unlocks the board and shows `AnalysisPanel`
- [ ] Giving up via the reveal path transitions immediately and automatically into Analysis Mode without requiring a button click
- [ ] Any legal move made in analysis mode appears in `MoveList` and does not advance the mainline guess cursor
- [ ] The "↩ Take back" button reverts the last analysis move; it is disabled when no analysis moves have been made
- [ ] Comments entered in `CommentEditor` are persisted and reloaded if the session is revisited
- [ ] The `ResumeBanner` is visible at all times during Analysis Mode and cannot be scrolled out of view
- [ ] Clicking "Resume Study" transitions the board to the next mainline guess position with the board interactive in Guess Mode
- [ ] Attempting to submit an analysis move via the API while `mode = 'guess'` returns `409`
- [ ] Applying a symbol to a move displays it inline in `MoveList` and persists across a page refresh
- [ ] Applying a symbol a second time removes it (toggle off)
- [ ] Submitting an invalid symbol value to `PUT /annotation` returns `400`
- [ ] Variation moves are stored in the `annotations` table and survive a page refresh

---

## Feature 5 — Annotated PGN Export

The user downloads their completed (or in-progress) session as a standard PGN file that includes inline comments, move symbols, and analysis variations — ready to import into Lichess, ChessBase, or any PGN viewer.

### Scope

- A single "Export PGN" button visible in `StudyView` at all times once a session has at least one completed position
- Spring loads the raw PGN and all annotation rows, assembles a structured game tree, and delegates serialisation to Python
- Python uses `python-chess` to reconstruct the game with inline comments (`{ }`) and variation lines (`( )`) in standard PGN notation
- The file is returned as a `text/plain` download with a sensible filename

### `chessmind-analysis` (Python)

- Add `export_pgn(pgn_raw: str, annotations: list[dict]) -> str` to `app/chess_engine.py`
  - Parse `pgn_raw` to get the mainline game object
  - Walk the mainline; for each move node look up a matching annotation by FEN and attach `comment` and `symbol` (rendered as a NAG: `$1` for `!`, `$2` for `?`, etc.)
  - Variation annotation rows (those with a `from_fen` that differs from the preceding mainline FEN) are appended as child variation nodes on the relevant parent node
  - Return the PGN string produced by `chess.pgn.Game.accept(chess.pgn.StringExporter())`
- Implement `POST /export` router in `app/routers/export.py`
  - Accept `{ pgn_raw: str, annotations: list[AnnotationNode] }` where `AnnotationNode` has `fen`, `from_fen`, `move_uci`, `move_san`, `comment`, `symbol`
  - Delegate to `chess_engine.export_pgn`; return `{ pgn: str }`
  - Return `400` if the PGN cannot be parsed
- Register the router in `app/main.py`
- Write `pytest` tests covering: export with no annotations, export with comments, export with symbols (NAG rendering), export with a variation line, invalid PGN returns 400

### `chessmind-api` (Spring / Kotlin)

- Add client-side DTOs: `ExportAnnotationNode(fen, fromFen, moveUci, moveSan, comment, symbol)` and `ExportRequest(pgnRaw, annotations)`; add `ExportResponse(pgn: String)`
- Add `suspend fun export(request: ExportRequest): ExportResponse` to `AnalysisClient` interface and implement in `AnalysisClientImpl`
- Add `suspend fun exportSession(sessionId: UUID): String` to `SessionService`
  - Load the `StudySession` (reject with 404 if not found)
  - Load all `Annotation` rows for the session via `AnnotationRepository.findAllBySessionId(sessionId)`
  - Map entities to `ExportAnnotationNode` instances
  - Call `analysisClient.export(ExportRequest(pgnRaw = session.pgnRaw, annotations = nodes))`
  - Return the PGN string
- Add `GET /api/v1/sessions/{id}/export` to `SessionController`
  - Call `sessionService.exportSession(id)`
  - Return `ResponseEntity` with `Content-Type: text/plain` and `Content-Disposition: attachment; filename="chessmind-{id}.pgn"`
- Unit test `SessionService.exportSession` with mocked repository and `AnalysisClient`

### `chessmind-ui` (Vue 3)

- Add `exportSession(sessionId: string): Promise<Blob>` to `src/services/sessionService.ts`
  - `GET /sessions/{id}/export` with `responseType: 'blob'`
  - Trigger a browser download using a temporary object URL
- Create `src/components/ExportButton.vue`
  - A single button labelled "Export PGN"
  - Disabled while `store.mode === 'guess'` and no moves have been completed yet (i.e., `store.currentMoveIdx === store.startMoveIdx`)
  - Shows a brief loading state while the request is in flight; resets on completion or error
  - Calls `exportSession(store.sessionId)` and triggers the download
- Render `<ExportButton />` in `StudyView.vue`, positioned alongside the board controls

### Acceptance Criteria

- [ ] Clicking "Export PGN" downloads a `.pgn` file with the correct filename
- [ ] The exported PGN is valid and importable into a standard PGN viewer
- [ ] Comments entered during analysis appear as `{ comment text }` in the exported PGN
- [ ] Move symbols are rendered as NAG codes (e.g., `!` → `$1`, `?` → `$2`)
- [ ] Analysis variation moves appear as parenthesised variation lines in the PGN
- [ ] The export button is disabled when no moves have been played yet
- [ ] A session with no annotations exports cleanly as a plain PGN of the mainline
- [ ] Exporting a non-existent session returns 404

---

## Feature 6 — Session History & Resume

Instead of re-uploading a PGN, the user lands on a session list that shows all their past games. They can resume any previous session — even if the Redis cache has expired — picking up exactly where they left off.

### Scope

- The application home page becomes a session list rather than the PGN import form
- Each session card shows the game title, players, progress, and status
- A "Resume" button re-initialises the Pinia store and navigates directly to `StudyView`
- A "New Game" button links to the existing `ImportView`
- If the Redis cache for a session has expired, Spring rehydrates it transparently before returning the session state

### `chessmind-api` (Spring / Kotlin)

- Add `plyCount: Int` column to `StudySession` entity and populate it during `createSession` (from `parseResponse.plyCount`); add a Flyway migration
- Add `AnnotationRepository.findAllBySessionId(sessionId: UUID): List<Annotation>`
- Add `StudySessionRepository.findAllByOrderByCreatedAtDesc(): List<StudySession>`
- Add DTOs: `SessionSummary(id, white, black, event, status, playerToGuess, currentMoveIdx, plyCount, createdAt)` and `LoadSessionResponse(id, white, black, event, playerToGuess, startMoveNum, currentMoveIdx, currentFen, mode, moves, plyCount)`
- Add `GET /api/v1/sessions` to `SessionController` — returns `List<SessionSummary>` sorted newest-first; sessions in `pending_setup` are included (partial state)
- Add `GET /api/v1/sessions/{id}` to `SessionController` — calls `sessionService.loadSession(id)`
- Add `suspend fun loadSession(sessionId: UUID): LoadSessionResponse` to `SessionService`
  - Load the `StudySession` from DB; 404 if missing
  - If `status == "pending_setup"` return a partial response (just metadata, no board state)
  - Try to read `session:{id}:progress` and `session:{id}:state` from Redis
  - If either key is absent (cache expired), rehydrate: re-call `analysisClient.parse(session.pgnRaw)`, re-populate both Redis keys with 24-hour TTL, derive `currentFen` from `session.currentMoveIdx`
  - Resolve `mode` from the Redis progress key (default `"guess"` if rehydrating after expiry)
  - Return `LoadSessionResponse` with full board state including the full move SAN list
- Unit test `SessionService.loadSession` for: cache-hit path, cache-miss rehydration path, pending\_setup session, non-existent session

### `chessmind-ui` (Vue 3)

- Add `listSessions(): Promise<SessionSummary[]>` and `getSession(id: string): Promise<LoadSessionResponse>` to `src/services/sessionService.ts`
- Add `loadSession(id: string): Promise<void>` action to the Pinia session store — calls `getSession`, populates all store fields from `LoadSessionResponse`, and navigates to `/study/:id`
- Create `src/views/SessionListView.vue`
  - On mount, calls `listSessions()` and renders a list/grid of session cards
  - Each card shows: `White vs Black` title, event name, status badge (`In Progress` / `Completed` / `Not started`), progress bar or fraction (`currentMoveIdx / plyCount`) for in-progress sessions
  - "Resume" button calls `store.loadSession(session.id)`; "Setup" button for `pending_setup` sessions navigates to `/setup/:id`
  - "New Game" button navigates to `/import`
  - Empty state: friendly message and a prominent "Import a Game" CTA when no sessions exist
- Update `src/router/index.ts`: `/` → `SessionListView`; `/import` remains; existing `/setup/:id` and `/study/:id` routes unchanged

### Acceptance Criteria

- [x] The home page (`/`) shows all past sessions sorted newest-first
- [x] Each session card displays both player names, event, status, and progress for in-progress sessions
- [x] Clicking "Resume" on an in-progress session opens `StudyView` at the correct position in the correct mode
- [x] Resuming a session whose Redis cache has expired works correctly (transparent rehydration)
- [x] Clicking "New Game" navigates to the import flow
- [x] A `pending_setup` session shows a "Setup" button that navigates to `/setup/:id`
- [x] An empty session list shows a helpful empty state rather than a blank page
- [x] `GET /sessions/{id}` for a missing session returns 404
- [x] `GET /sessions` returns an empty array (not 404) when no sessions exist
