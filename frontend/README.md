# Cheaply Frontend

React 19 + TypeScript + TailwindCSS 4, built with Vite.

- `npm run dev` - dev server on :5173, proxying `/api` to the backend on :8080
- `npm run build` - type-check (`tsc -b`) and production build
- Deployed via the multi-stage `Dockerfile` (build -> nginx) in docker-compose

Key structure:

```
src/
  lib/api.ts        typed fetch client (envelope unwrap, 401 -> refresh -> retry)
  lib/auth.ts       token storage + single-flight refresh rotation
  context/          AuthContext (session restore, login/signup/logout)
  pages/            Search, Login, Signup, History
  components/       Layout, ProductCard, ErrorBoundary
  types/api.ts      TypeScript mirror of the backend contract
```
