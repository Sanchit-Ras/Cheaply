# Cheaply V3 - Completion Plan

V2 rebuilt the backend (Spring Boot) and hardened both services. V3 finishes
the project: closes the remaining infrastructure gaps and builds the frontend
that has been missing since the V1 Flask UI was deleted.

Stack for the new frontend: **React 18 + TypeScript + TailwindCSS (Vite)**.
Deliberate non-choices: no Redux (context + hooks suffice at this scale), no
component library (Tailwind is the styling story), no axios (typed fetch
wrapper).

---

## Phase 0 - Finish the foundation

The frontend needs a stable, runnable API to build against.

- [x] `docker-compose.yml`: drop obsolete `version:`, stop publishing the
      scraper port, wire a shared `SCRAPER_API_KEY` into both services, move
      all secrets to a root `.env` (`env_file:`), bind Postgres/Redis to
      127.0.0.1, add a Redis password, add a backend healthcheck (actuator).
- [x] Root `.env.example`, `.gitignore`, `README.md`.
- [x] CI (`.github/workflows/ci.yml`): backend `./gradlew test` + scraper
      `pytest` on push and pull request.
- [x] End-to-end smoke test: `docker compose up`, then a real search through
      backend -> scraper -> live stores. Never verified since the V2 fixes.

**Exit criteria:** one command brings the stack up; a real search returns
ranked products.

## Phase 1 - Frontend scaffold

- [x] `frontend/` at project root: Vite + React + TS + Tailwind,
      `react-router-dom` as the only significant dependency.
- [x] TypeScript types mirroring the backend contract: `ApiResponse<T>`,
      `Product` (`price_per_unit`, `rank`, `bestValue`, `unit`),
      `StoreStatus`, `SearchResponse`, auth DTOs.
- [x] Typed fetch client with `VITE_API_BASE_URL`; app shell (header, routes).

**Exit criteria:** app boots and reads `/actuator/health` through the client.

## Phase 2 - Search experience (largest phase; works logged-out)

- [x] Search page -> `POST /api/search` -> results grouped **by unit**
      (per-kg and per-litre sections - the backend ranks within units, the UI
      must not flatten that), "Best value" badge, source, product links.
- [x] The 45-second problem: cold searches take up to ~45s. Staged loading
      messages, not a frozen spinner. Show the `cached` flag subtly.
- [x] Partial-results banner naming the failed store(s) from `stores`.
- [x] 429 handling: honour `Retry-After` with a disabled button + countdown.

**Exit criteria:** anonymous search works end to end; degraded states honest.

## Phase 3 - Authentication

- [x] Signup/login with client-side validation matching backend rules
      (username charset; password >= 8 chars with letter + digit).
- [x] Auth context; tokens in localStorage (XSS trade-off documented).
- [x] Refresh interceptor: access tokens live 15 min, refresh tokens are
      single-use (rotated). Concurrent 401s must share one deduplicated
      refresh call or the second burns a rotated token and force-logs-out.
- [x] Logout -> `POST /api/auth/logout` + clear storage; `/me` in the header.

**Exit criteria:** session survives a 15-minute expiry invisibly.

## Phase 4 - Search history

- [x] Recent searches (search response + `GET /api/history`), click to
      re-run, clear-all with confirmation.

## Phase 5 - Integration, polish, ship

- [x] Frontend Dockerfile (build -> nginx) + compose service; nginx proxies
      `/api` so production has no CORS at all.
- [x] CI: add `tsc --noEmit` + `vite build` job.
- [x] Responsive pass, empty states, error boundaries.
- [x] README covering the full stack; final end-to-end pass.
- [ ] New git repository created by the project owner (root repo, clean
      initial commit).

---

## Sizing and order

| Phase | Size   | Depends on |
|-------|--------|------------|
| 0     | Small  | -          |
| 1     | Small  | 0          |
| 2     | Largest| 1          |
| 3     | Medium | 1          |
| 4     | Small  | 3          |
| 5     | Medium | 2-4        |
