# chessmind-ui

Vue 3 + TypeScript frontend for the chessmind application.

## Running the full stack

The easiest way to run everything is from the repo root:

```sh
docker compose up --build
```

This starts Postgres, Redis, the analysis microservice, the Spring API, and the UI dev server together.

## Running only the backend services (for UI development)

When working on the frontend, start just the infrastructure and backend services, then run the UI dev server locally for hot-reload:

```sh
# From the repo root — start Postgres, Redis, analysis service, and API
docker compose up postgres redis chessmind-analysis chessmind-api
```

Then in a separate terminal, from this directory (`chessmind-ui/`):

```sh
npm install
npm run dev       # starts at http://localhost:5173
```

The Vite dev server proxies `/api/v1` requests to `http://localhost:8080` automatically.

## Individual Docker service commands

All `docker compose` commands must be run from the **repo root** (the directory containing `docker-compose.yml`), not from `chessmind-ui/`.

### Postgres

```sh
docker compose up postgres          # start
docker compose stop postgres        # stop (data preserved)
docker compose down postgres        # stop and remove container (data preserved)
docker compose down -v postgres     # stop, remove container, and wipe the database volume
```

### Redis

```sh
docker compose up redis             # start
docker compose stop redis           # stop
docker compose down redis           # stop and remove container
```

### Analysis microservice (`chessmind-analysis`)

```sh
docker compose up chessmind-analysis          # start
docker compose up --build chessmind-analysis  # rebuild image and start
docker compose stop chessmind-analysis        # stop
docker compose down chessmind-analysis        # stop and remove container
```

### Spring API (`chessmind-api`)

Requires Postgres, Redis, and the analysis service to be running first.

```sh
docker compose up chessmind-api          # start
docker compose up --build chessmind-api  # rebuild image and start
docker compose stop chessmind-api        # stop
docker compose down chessmind-api        # stop and remove container
```

### UI dev server (`chessmind-ui`)

Only needed when running the full stack via Docker. For active frontend development, run `npm run dev` locally instead (see [Frontend commands](#frontend-commands)).

```sh
docker compose up chessmind-ui          # start
docker compose up --build chessmind-ui  # rebuild image and start
docker compose stop chessmind-ui        # stop
docker compose down chessmind-ui        # stop and remove container
```

## Stopping all Docker services

Stop all running services (containers stay, volumes preserved):

```sh
docker compose stop
```

Stop and remove containers (volumes preserved — Postgres data survives):

```sh
docker compose down
```

Stop and remove containers **and** volumes (wipes the Postgres database):

```sh
docker compose down -v
```

## Service URLs

| Service               | URL                       |
| --------------------- | ------------------------- |
| UI (dev)              | http://localhost:5173     |
| Spring API            | http://localhost:8080     |
| Analysis microservice | http://localhost:8000     |
| Postgres              | localhost:5432            |
| Redis                 | localhost:6379            |

Postgres credentials: database `chessmind`, user `chessmind`, password `chessmind`.

## Running backend services without Docker

**Analysis microservice** — from `chessmind-analysis/`:

```sh
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

**Spring API** — from `api/api/`:

```sh
./gradlew bootRun
```

The API defaults to `localhost:5432` for Postgres, `localhost:6379` for Redis, and `http://localhost:8000` for the analysis service, so all three must be running before starting the API.

## Frontend commands

```sh
npm install           # install dependencies
npm run dev           # dev server with hot-reload (port 5173)
npm run build         # type-check + production build
npm run test:unit     # Vitest unit tests
npm run type-check    # TypeScript check only
npm run lint          # oxlint + eslint with auto-fix
npm run format        # Prettier
```

## IDE setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (disable Vetur if installed).

- Chrome/Edge/Brave: [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
- Firefox: [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
