# PROPOSAL: Chess Move Study Application

> A "Guess the Move" interactive chess study tool for learning from the games of great players.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack & Rationale](#2-tech-stack--rationale)
3. [System Architecture](#3-system-architecture)
4. [Service Breakdown](#4-service-breakdown)
5. [Data Models](#5-data-models)
6. [API Design](#6-api-design)
7. [Frontend Design](#7-frontend-design)
8. [Feature Roadmap](#8-feature-roadmap)
9. [Infrastructure & DevOps](#9-infrastructure--devops)

---

## 1. Project Overview

**Working Title:** `chessmind` (or similar)

A single-page web application where a user can import a PGN of a famous game, select a player and starting move, and interactively guess that player's moves one at a time. Between guesses, the user may freely explore the position — making variations, writing comments — just like a Lichess study. When they're ready to resume, the application prompts for the next move in the game. The resulting analysis is saved per-session and can be exported as an annotated PGN.

### Core User Journey

```
Import PGN → Pick Player + Start Move → Guess Move N
  → (Correct/Incorrect feedback) → Free Analysis Mode
  → "Ready to continue?" → Guess Move N+1 → ... → Export PGN
```

---

## 2. Tech Stack

### Backend — Spring Boot 3 + Kotlin

The API layer is built on **Spring Boot 3** with **Kotlin**. Kotlin's data classes, sealed classes, null safety, and coroutine support make REST API and domain modeling code concise and expressive on the JVM. Spring Boot 3 brings Jakarta EE namespacing, native AOT compilation support, and virtual thread integration via Project Loom. Persistence is handled by **Spring Data JPA** with Hibernate against a **PostgreSQL** database, which suits the relational nature of sessions, annotation trees, and PGN metadata.

### Analysis Microservice — Python + FastAPI

All chess logic is isolated in a dedicated **Python** microservice built with **FastAPI**. The `python-chess` library provides authoritative PGN parsing, FEN generation, move validation, and optional Stockfish engine integration. FastAPI provides async request handling and automatic OpenAPI documentation. This service is internal-only and is never exposed directly to the browser; Spring is the sole caller.

**Redis** serves a dual role: a short-lived session state cache (active board position, move cursor, in-progress variation stack) to avoid redundant database reads on every move interaction, and a lightweight channel for coordinating state between the Spring and Python layers.

### Frontend — Vue 3 + TypeScript

The client is a **Vue 3** single-page application using the `<script setup>` Composition API with **TypeScript** throughout. **Pinia** is used for client-side state management. Chessboard rendering is handled by [`cm-chessboard`](https://github.com/shaack/cm-chessboard) or [`vue3-chessboard`](https://github.com/qwerty084/vue3-chessboard), both of which are sufficiently headless to support the custom guess/analysis interaction model required by this application.

### Infrastructure

All services are containerized with **Docker** and orchestrated locally via **Docker Compose**. CI/CD is handled by **GitHub Actions**, covering build, test, image publication to GitHub Container Registry, and deployment.

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Browser (Vue 3)                       │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐  │
│  │  PGN Import  │  │  Chess Board  │  │  Analysis Panel  │  │
│  │  & Setup     │  │  (Guess Mode) │  │  (Free Explore)  │  │
│  └──────┬───────┘  └───────┬───────┘  └────────┬─────────┘  │
└─────────┼──────────────────┼───────────────────┼────────────┘
          │  REST/JSON        │                   │
          ▼                  ▼                   ▼
┌──────────────────────────────────────────────────────────────┐
│              Spring Boot 3 API (Kotlin)                       │
│                                                              │
│  SessionController  MoveController  AnnotationController     │
│         │                 │                  │               │
│         └─────────────────┴──────────────────┘               │
│                           │                                  │
│                    Service Layer                             │
│              ┌────────────┴────────────┐                     │
│              │                         │                     │
│         PostgreSQL               Redis Cache                 │
│   (sessions, annotations,        (active board state,       │
│    exported PGNs)                 move cursor, FEN)          │
│              │                                               │
│              │  Internal HTTP (REST)                         │
└──────────────┼───────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│     Python Analysis Service          │
│                                      │
│  /parse-pgn    → move list + FENs    │
│  /validate     → is move legal?      │
│  /compare      → guess vs actual     │
│  /export-pgn   → annotated PGN text  │
│                                      │
│  Libraries: python-chess, FastAPI    │
└──────────────────────────────────────┘
```

**Communication pattern:**
- Vue ↔ Spring: REST/JSON over HTTP. All user actions hit Spring.
- Spring ↔ Python: Internal synchronous REST calls (FastAPI). Spring is the only caller; Python is never exposed to the public.
- Spring ↔ Redis: Session state cache. The active `StudySession` (current FEN, move index, variation tree) lives in Redis during a study session, persisted to PostgreSQL on save/export.

---

## 4. Service Breakdown

### 4.1 Spring Boot API (`chessmind-api`)

Responsibilities:
- User session management (can be stateless JWT or simple session token for v1)
- Receives PGN text from the client, forwards to Python for parsing, stores result
- Orchestrates the guess-the-move loop: serves masked move prompts, validates submissions via Python, tracks progress
- Stores annotation trees (variations + comments) in PostgreSQL
- Exposes a PGN export endpoint that asks Python to serialize the annotated game

Key packages:
```
com.chessmind
├── session/          # StudySession lifecycle (create, load, save)
├── move/             # Move submission, validation, feedback
├── annotation/       # Comments, variations per position
├── export/           # PGN assembly and download
├── analysis/         # HTTP client to Python service
└── config/           # Redis, JPA, Security config
```

Technology choices within Spring:
- **Spring Data JPA** with Hibernate for PostgreSQL
- **Spring Data Redis** (`RedisTemplate` or Spring Cache abstraction) for session state
- **Spring WebClient** (reactive HTTP client) for non-blocking calls to the Python service
- **Kotlin Coroutines + Spring's coroutine support** to keep controller and service code clean and non-blocking

### 4.2 Python Analysis Service (`chessmind-analysis`)

Built with **FastAPI** for its speed, async support, and automatic OpenAPI docs.

Endpoints:

| Method | Path | Description |
|---|---|---|
| `POST` | `/parse` | Accept PGN string → return structured move list with FENs, player names, headers |
| `POST` | `/validate` | Accept FEN + UCI move → return `{legal: bool, san: string}` |
| `POST` | `/compare` | Accept FEN + guessed move + actual move → return `{correct: bool, actual_san, actual_uci, fen_after}` |
| `POST` | `/export` | Accept annotated game tree → return PGN string with comments and variations |

Libraries:
- `python-chess` — all board logic, PGN parsing, FEN handling
- `fastapi` + `uvicorn` — HTTP layer
- `stockfish` (optional, Phase 2) — engine evaluation of guesses

### 4.3 Vue 3 Frontend (`chessmind-ui`)

Key views:
- **`ImportView`** — Textarea for PGN input, player selector (derived from PGN headers), move number slider
- **`StudyView`** — The main study interface. Two modes:
  - *Guess Mode*: Board is interactive but only the correct move advances the game. Incorrect moves show feedback and reset.
  - *Analysis Mode*: Full board freedom — user can make any moves, enter comments, build variations. A "Resume Study" button is always visible.
- **`ExportView`** — Preview of the annotated PGN, download button

State shape (Pinia):
```typescript
interface StudyStore {
  sessionId: string
  pgn: string
  playerToGuess: 'white' | 'black'
  startMove: number
  currentMoveIndex: number
  currentFen: string
  mode: 'guess' | 'analysis'
  variationTree: VariationNode[]
  lastGuessResult: GuessResult | null
}
```

---

## 5. Data Models

### PostgreSQL

```sql
-- Stores an imported game and its metadata
CREATE TABLE study_sessions (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  pgn_raw     TEXT NOT NULL,           -- original imported PGN
  white       VARCHAR(100),
  black       VARCHAR(100),
  event       VARCHAR(200),
  player_to_guess  VARCHAR(5) NOT NULL CHECK (player_to_guess IN ('white', 'black')),
  start_move_num   INT NOT NULL DEFAULT 1,
  current_move_idx INT NOT NULL DEFAULT 0,
  status      VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                CHECK (status IN ('in_progress', 'completed'))
);

-- Annotation tree: each node is a position (FEN) with a comment and/or child variations
CREATE TABLE annotations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id  UUID NOT NULL REFERENCES study_sessions(id) ON DELETE CASCADE,
  parent_id   UUID REFERENCES annotations(id),  -- null = mainline
  fen         VARCHAR(100) NOT NULL,
  move_san    VARCHAR(20),                       -- the move that led to this FEN
  move_uci    VARCHAR(10),
  comment     TEXT,
  move_order  INT NOT NULL DEFAULT 0,
  is_mainline BOOLEAN NOT NULL DEFAULT false
);
```

### Redis (session cache)

```
KEY: session:{sessionId}:state
TYPE: JSON string (or Redis Hash)
TTL: 24 hours (refreshed on each interaction)

VALUE: {
  "currentFen": "rnbqkbnr/...",
  "moveIndex": 14,
  "mode": "analysis",
  "movelist": ["e4", "e5", ...],   // full mainline from python-chess parse
  "variationStack": [...]           // active analysis line (not yet persisted)
}
```

---

## 6. API Design

All Spring endpoints are prefixed `/api/v1`.

### Sessions

| Method | Path | Body | Description |
|---|---|---|---|
| `POST` | `/sessions` | `{pgn, playerToGuess, startMove}` | Parse PGN, create session, return session ID and initial state |
| `GET` | `/sessions/{id}` | — | Load session (board state, progress) |
| `PUT` | `/sessions/{id}` | `{comment?, variationMoves?}` | Save analysis from current position |

### Moves

| Method | Path | Body | Description |
|---|---|---|---|
| `POST` | `/sessions/{id}/guess` | `{uciMove}` | Submit a guess. Returns `{correct, actualMove, fen, feedback}` |
| `POST` | `/sessions/{id}/analysis/move` | `{uciMove}` | Make a free move in analysis mode (adds to variation tree) |
| `DELETE` | `/sessions/{id}/analysis/move` | — | Take back last analysis move |
| `POST` | `/sessions/{id}/resume` | — | Exit analysis mode, advance to next guess prompt |

### Export

| Method | Path | Description |
|---|---|---|
| `GET` | `/sessions/{id}/export` | Returns annotated PGN file as `text/plain` |

---

## 7. Frontend Design

### Component Tree

```
App.vue
├── RouterView
│   ├── ImportView.vue
│   │   ├── PgnTextarea.vue
│   │   ├── PlayerSelector.vue
│   │   └── StartMoveSlider.vue
│   └── StudyView.vue
│       ├── ChessBoard.vue            ← wraps cm-chessboard or vue3-chessboard
│       ├── MoveGuessPanel.vue        ← shown in 'guess' mode
│       │   └── GuessFeedback.vue
│       ├── AnalysisPanel.vue         ← shown in 'analysis' mode
│       │   ├── MoveList.vue          ← mainline + variations
│       │   ├── CommentEditor.vue
│       │   └── ResumeBanner.vue      ← sticky "Resume Study" CTA
│       └── ExportButton.vue
```

### Key UX Details

- **Guess Mode**: The board accepts moves. If the move is wrong, the piece snaps back and feedback is shown (e.g., "Not quite — try again" or optionally reveal after N attempts). If correct, the opponent's response is played automatically, then the board enters Analysis Mode.
- **Analysis Mode**: The `ResumeBanner` is always visible as a sticky bar at the top. The move list panel shows the current variation path. Comments are auto-saved on blur.
- **PGN Import**: Validate PGN client-side before submitting (use a lightweight JS chess lib like `chess.js` for instant feedback). Extract player names from PGN headers to populate the player selector dropdown.

---

## 8. Feature Roadmap

### Phase 1 — Core MVP

- [ ] PGN import and parsing via Python service
- [ ] Player + start move selection
- [ ] Guess-the-move loop with correct/incorrect feedback
- [ ] Free analysis mode (moves + comments) between guesses
- [ ] Resume study flow
- [ ] Annotated PGN export
- [ ] PostgreSQL persistence of sessions and annotations
- [ ] Redis session state cache
- [ ] Docker Compose for local development

### Phase 2 — Enhanced Analysis

- [ ] Stockfish integration in the Python service to score guesses (centipawn loss, best move comparison)
- [ ] Post-game summary: accuracy score per guess, average centipawn loss
- [ ] Variation tree visualized as a collapsible move list (Lichess-style)
- [ ] Keyboard shortcuts for move navigation (← → arrows, `Esc` to reset)

### Phase 3 — Multi-User & Polish

- [ ] User accounts (Spring Security + JWT)
- [ ] Save/load multiple sessions per user
- [ ] Share a study session via link (read-only view)
- [ ] Opening name detection (ECO codes via `python-chess` opening book)
- [ ] Mobile-responsive board layout

### Phase 4 — Productionization

- [ ] GitHub Actions CI/CD pipeline (build → test → push Docker images → deploy)
- [ ] Deploy to AWS (EC2 or ECS) on Amazon Linux to mirror current professional environment
- [ ] HTTPS via Caddy or nginx reverse proxy
- [ ] Structured logging (Logback + JSON) and basic metrics (Spring Actuator)

---

## 9. Infrastructure & DevOps

### Docker Compose (Local Dev)

```yaml
# docker-compose.yml
services:
  api:
    build: ./chessmind-api
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/chessmind
      SPRING_REDIS_HOST: redis
      ANALYSIS_SERVICE_URL: http://analysis:8000
    depends_on: [db, redis, analysis]

  analysis:
    build: ./chessmind-analysis
    ports: ["8000:8000"]

  ui:
    build: ./chessmind-ui
    ports: ["5173:5173"]
    environment:
      VITE_API_BASE_URL: http://localhost:8080/api/v1

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: chessmind
      POSTGRES_USER: chessmind
      POSTGRES_PASSWORD: chessmind
    volumes: ["pgdata:/var/lib/postgresql/data"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  pgdata:
```

### GitHub Actions CI Pipeline

```
on: push to main / pull_request

jobs:
  test-api:     → ./gradlew test (Kotlin + Spring)
  test-analysis: → pytest (Python)
  test-ui:      → vitest (Vue + TypeScript)
  build-images: → docker build all three services
  push-images:  → push to GitHub Container Registry (ghcr.io)
  deploy:       → SSH to EC2 + docker compose pull + up (Phase 4)
```

### Project Monorepo Layout

```
chessmind/
├── chessmind-api/        # Spring Boot 3 + Kotlin
│   ├── src/
│   ├── build.gradle.kts
│   └── Dockerfile
├── chessmind-analysis/   # FastAPI + python-chess
│   ├── app/
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
├── chessmind-ui/         # Vue 3 + TypeScript
│   ├── src/
│   ├── vite.config.ts
│   └── Dockerfile
├── docker-compose.yml
├── docker-compose.prod.yml
└── .github/
    └── workflows/
        └── ci.yml
```


