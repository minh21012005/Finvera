# Finvera Frontend

Private React 19 single-page client built with Vite and TypeScript. The browser
calls only the Spring Boot API and never calls market-data providers or the
internal AI service directly.

## Commands

```powershell
npm install
npm run dev
npm run test
npm run lint
npm run build
```

The Vite development server uses the address printed by `npm run dev`. Runtime
API calls must use same-origin `/api/v1/*` endpoints; do not put secrets in
`VITE_*` variables because they are included in the browser bundle.

Feature 001 behavior and delivery tasks are defined under
`../specs/001-market-overview/`.
