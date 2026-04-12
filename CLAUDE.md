# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

chessmind is a monorepo containing three services:

- `chessmind-api` — Spring Boot 3 + Kotlin REST API (port 8080)
- `chessmind-analysis` — FastAPI + python-chess analysis microservice (port 8000)
- `chessmind-ui` — Vue 3 + TypeScript single-page application (port 5173)

PostgreSQL and Redis run as Docker services alongside the three application services.

## Running locally

```bash
docker compose up --build
```

## Service commands (without Docker)

**chessmind-api** (run from `api/api/`):
```bash
./gradlew bootRun        # start server
./gradlew test           # run all tests
./gradlew test --tests "com.chessmind.api.SomeServiceTest"  # single test class
```

**chessmind-analysis** (run from `chessmind-analysis/`):
```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000   # start server
pytest                                       # run all tests
pytest tests/test_foo.py::test_bar           # single test
```

**chessmind-ui** (run from `chessmind-ui/`):
```bash
npm install
npm run dev          # start dev server (port 5173)
npm run test:unit    # run Vitest tests
npm run type-check   # TypeScript check
npm run lint         # oxlint + eslint with auto-fix
npm run format       # Prettier
```

## Absolute rules

1. **Chess logic belongs exclusively in `chessmind-analysis`.** Never implement move validation, PGN parsing, FEN generation, or board state calculations in `chessmind-api` or `chessmind-ui`.
2. **`chessmind-analysis` is never called from the browser.** All client traffic goes through `chessmind-api`. The Python service has no public-facing route.
3. **Redis is a cache, not a database.** Every piece of data stored in Redis must also be persistable to PostgreSQL. Never treat Redis as the sole source of truth for user data.
4. **`chessmind-api` never interprets chess data.** PGN and FEN strings are opaque to the Spring service — it stores and forwards them, but never parses or acts on their chess meaning.

## Service responsibilities

| Service | Owns |
|---|---|
| `chessmind-api` | Session lifecycle, PostgreSQL persistence, HTTP surface, orchestrating the guess loop |
| `chessmind-analysis` | All chess logic, PGN parsing, move validation, FEN generation, annotated PGN export |
| `chessmind-ui` | Presentation, user input, Pinia store state, API calls through the service layer only |

## `chessmind-api` conventions (Kotlin / Spring Boot 3)

- Use Kotlin data classes for all DTOs and domain objects — no Java-style POJOs.
- Use `suspend` functions and coroutines throughout. Do not use `Mono`/`Flux`.
- All calls to `chessmind-analysis` go through the `AnalysisClient` interface. Controllers and services must not call the analysis service directly via ad-hoc HTTP calls.
- Place `@Transactional` on service methods only, never on controllers.
- Entities must never leave the service package. Always map to a DTO before returning from a service method.
- All inter-service HTTP calls must have an explicit timeout configured.

## `chessmind-analysis` conventions (Python / FastAPI)

- All endpoints are stateless. Each request must contain everything the handler needs (FEN, PGN, move list, etc.). No session state is held in the Python service.
- All `python-chess` interactions are routed through `app/chess_engine.py`. FastAPI route handlers delegate to it — they do not call `python-chess` directly.
- Type-annotate all function signatures. Use Pydantic models for all request and response bodies.

## `chessmind-ui` conventions (Vue 3 / TypeScript)

- `strict: true` is on — `any` is not permitted.
- All API calls go through `src/services/` — components never call `fetch` or axios directly.
- Board state lives in the Pinia store, not in component-local `ref`s.
- `StudyView` must not contain logic that evaluates whether a guess is correct — that responsibility belongs to the store, which delegates to the API.

## Testing expectations

- **Python:** every endpoint has at least one happy-path and one error-path `pytest` test.
- **Spring:** service methods are unit tested with mocked repositories; controllers are tested with `@WebMvcTest`.
- **Vue:** non-trivial component logic is tested with Vitest. Do not write tests for Pinia actions that are pure API pass-throughs.

## Commit style

Conventional Commits with service scope where relevant:

```
feat(api): add session setup endpoint
feat(analysis): implement /compare endpoint
fix(ui): correct move index off-by-one in StudyView
chore: update docker-compose postgres version
```