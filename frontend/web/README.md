# Tradify Web

React/Vite frontend for paper-trading account projections. It communicates only with the Account Service and keeps Keycloak tokens in the adapter's memory.

## Run

1. Copy `.env.example` to `.env` and supply the public environment values.
2. Run `npm install`.
3. Run `npm run dev`.

Use an SPA fallback in deployment, including `/settings/connections/*`, so Alpaca OAuth can return to the client. Do not add credentials, OAuth values, or secret references to the browser configuration.

The build embeds the four public `VITE_*` values. A browser should normally open the application through an HTTP server (for example, `npm run preview`), not by double-clicking `dist/index.html`. The build uses relative asset URLs so a direct preview can still show a configuration message rather than a blank page.
