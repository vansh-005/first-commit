# Recollect — frontend

React 19 + TypeScript + Vite, Tailwind CSS, `oidc-client-ts` for Cognito sign-in. See the [root README](../README.md)
for the product and architecture overview.

```bash
npm ci
cp .env.example .env.local   # fill in values from your own deployment
npm run dev                  # local dev server
npx tsc --noEmit             # typecheck
npm run lint                 # oxlint
npm test                     # vitest
npm run build                # production build (deployed by Amplify on Git push)
```

- `src/pages/` — Landing, Login, Home, Library, Search, Ask and the public `/engineering` page
- `src/components/` — shared UI (layout, library, search, ask, engineering, marketing)
- `src/api/` — typed API client; the frontend never chooses S3 keys, model IDs or tenant filters
- `src/auth/` — Cognito OIDC configuration
- `../Docs/diagrams/` — canonical SVG diagrams, inlined into `/engineering`

Design rules live in [`../Docs/FRONTEND.md`](../Docs/FRONTEND.md).
