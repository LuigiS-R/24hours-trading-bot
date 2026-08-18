# Tradify Web

The React single-page application for **Tradify**, a paper-trading-only algorithmic trading project. It is deliberately a browser client of the Accounts Service: it does not call Alpaca, the database, RabbitMQ, or the AI services directly.

> **Current status — prototype / integration work in progress.** The application has a working Vite/React shell, Keycloak adapter integration, route protection, typed request helpers, and initial pages for accounts and watchlists. It is not yet a production-ready front end. In particular, the current Accounts Service response shapes do not fully match the shapes assumed by the UI, and no front-end automated tests have been added yet. See [Known integration gaps](#known-integration-gaps) and [Backlog](#backlog-for-the-front-end-team) before extending it.

## Scope and safety boundaries

- **Paper trading only.** The UI must never imply that real-money orders can be placed.
- **Accounts Service is the only browser API.** Browser code uses `/api/v1/**` endpoints on that service.
- **Keycloak owns credentials and sessions.** The application never presents its own username/password form and never persists access tokens in browser storage.
- **Alpaca OAuth is server-side.** The browser asks Accounts Service for an authorization URL, then follows it. Accounts Service performs the code exchange and keeps Alpaca tokens in its secret store.
- **No secrets in `VITE_*` variables.** Vite embeds these values into the compiled browser bundle. They may contain public URLs, realm names, and public client IDs only—never an Alpaca client secret, API key, database credential, OAuth code, token, or secret reference.

## Project map

```text
web/
├── src/
│   ├── api/             # Accounts Service request helper and currently assumed DTOs
│   ├── auth/            # Keycloak configuration, provider, and route guard
│   ├── components/      # Shared shell, status, confirmation, and display helpers
│   ├── pages/           # Login, dashboard, connections, watchlists, profile, OAuth pages
│   ├── styles/          # Responsive dark-theme global styles
│   ├── App.tsx          # Route table
│   └── main.tsx         # Application bootstrap and missing-config fallback
├── .env.example         # Safe public development configuration template
├── package.json
└── vite.config.ts       # Relative static-asset base
```

The wider repository contains the service this application currently consumes at `../../account-service`, plus market-data, news-ingestion, order-execution, and AI services. Those other services are not direct front-end dependencies today.

## What is implemented

| Area               | Current behavior                                                                                                                                                                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authentication     | Initializes Keycloak with `check-sso`, PKCE (`S256`), and no login-status iframe. Login, registration, logout, protected routes, token refresh before requests, and 401/403 handling are present. Tokens remain in the Keycloak adapter’s memory. |
| App shell          | Responsive dark layout, always-visible `PAPER TRADING` label, navigation, current Keycloak username, account selector, status/freshness badges, loading/error/confirmation states.                                                                |
| Profile            | Loads the signed-in user profile and updates the display name through Accounts Service.                                                                                                                                                           |
| Connections        | Lists connections, starts the server-created Alpaca OAuth flow, provides manual reconcile and confirmed disconnect actions.                                                                                                                       |
| Dashboard          | Requests the aggregate dashboard and displays aggregate freshness/totals plus an empty connection state.                                                                                                                                          |
| Watchlists         | Lists a selected connection’s watchlists; creates watchlists; displays a detail view; adds/removes symbols; toggles the local active flag; confirms deletions. Broker-changing requests send a new `Idempotency-Key` per action.                  |
| Defensive handling | Invalid UUID route/query values are rejected before a request. API error rendering removes a `secretReference` property recursively before displaying a response.                                                                                 |

## Keycloak: why it is central

Keycloak is the application’s identity provider and the trust boundary between the browser and the backend. It is not merely a login screen.

```mermaid
sequenceDiagram
    participant Browser as Tradify Web
    participant KC as Keycloak
    participant Accounts as Accounts Service
    participant Alpaca as Alpaca Paper

    Browser->>KC: Login/register using Keycloak-hosted pages
    KC-->>Browser: OIDC access token
    Browser->>Accounts: API request + Bearer token
    Accounts->>KC: Validate JWT signature, issuer, expiry
    Accounts-->>Browser: User-owned account/projection data
    Browser->>Accounts: Start Alpaca connection
    Accounts-->>Browser: Authorization URL
    Browser->>Alpaca: Navigate to OAuth authorization
    Alpaca->>Accounts: Callback with code + state
    Accounts->>Alpaca: Exchange code server-side; store token securely
    Accounts-->>Browser: Redirect to connection-success route
```

Important consequences for front-end work:

1. The browser authenticates to **Keycloak**, then sends its access token to Accounts Service as `Authorization: Bearer <token>`.
2. Accounts Service validates the token against `KEYCLOAK_ISSUER_URI` and uses the JWT `sub` claim to find/provision the local user. A user ID must never be supplied by the browser to select an account.
3. A Keycloak **public SPA client** is required. It must use Authorization Code Flow with PKCE; do not configure or expose a client secret in the SPA.
4. Keycloak must allow the exact browser origin(s) used by development and deployment in its valid redirect URIs, web origins/CORS settings, and post-logout redirect URIs. For local Vite this normally includes `http://localhost:5173/*` (or the actual Vite port).
5. `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, the front-end client ID, and Accounts Service’s issuer URI must describe the **same Keycloak realm**. The repository’s example and backend defaults currently disagree; resolve that before integration testing.

### Required Keycloak configuration

The repository does not include a realm export or container configuration. A platform/backend owner must provide a reachable realm and configure a client approximately as follows:

| Setting                         | Local-development value                                                  |
| ------------------------------- | ------------------------------------------------------------------------ |
| Realm                           | The value used in `VITE_KEYCLOAK_REALM` and the backend issuer path      |
| Client ID                       | `VITE_KEYCLOAK_CLIENT_ID`                                                |
| Client type                     | Public client / SPA                                                      |
| Standard flow                   | Enabled                                                                  |
| PKCE                            | S256                                                                     |
| Valid redirect URIs             | `http://localhost:5173/*` (adjust for the actual port)                   |
| Valid post-logout redirect URIs | `http://localhost:5173/login`                                            |
| Web origins                     | `http://localhost:5173`                                                  |
| User registration               | Enable only if the team wants the in-app “Create account” button to work |

Accounts Service also needs its resource-server issuer set to the realm issuer URL, for example `http://localhost:<keycloak-port>/realms/<realm>`. It must be able to retrieve the realm’s OIDC discovery and signing keys.

## Configuration

Copy the template and edit only public values:

```bash
cp .env.example .env
```

```dotenv
VITE_KEYCLOAK_URL=http://localhost:8085
VITE_KEYCLOAK_REALM=trading-bot
VITE_KEYCLOAK_CLIENT_ID=trading-bot-frontend-client
VITE_ACCOUNTS_API_BASE_URL=http://localhost:8081
```

Vite reads environment variables when it starts/builds. Restart `npm run dev` after changing `.env`, and rebuild before deploying changed values.

The startup UI checks only the three Keycloak variables. Set `VITE_ACCOUNTS_API_BASE_URL` too: if it is empty, requests become same-origin `/api/...` requests, which is only valid behind a correctly configured reverse proxy.

## Run and test locally

### 1. Prerequisites

- Node.js 20 LTS or newer and npm.
- A Keycloak realm/client configured as described above, plus a test user (or enabled registration).
- Accounts Service running with PostgreSQL, the same Keycloak issuer, and its Alpaca configuration. See `../../account-service/API_DOCUMENTATION.md` and its `src/main/resources/application.properties` for the backend prerequisites.
- An Alpaca **paper** OAuth application if testing the connection flow end-to-end. Its redirect URI must exactly match the Accounts Service callback, normally `http://localhost:<accounts-port>/api/v1/alpaca/oauth/callback`.

There is no repository-wide Docker Compose or Keycloak realm export at present, so local infrastructure must be supplied/configured separately.

### 2. Install and start the front end

From this directory:

```bash
npm ci
npm run dev
```

Vite will print the local URL (normally `http://localhost:5173`). Open that URL, not `dist/index.html` directly. Use the printed port in the Keycloak client configuration if Vite selects another one.

To check the release build:

```bash
npm run build
npm run preview
```

`npm run build` type-checks with TypeScript and creates `dist/`; `npm run preview` serves that build locally. The project currently has no `test`, lint, unit-test, end-to-end-test, or coverage script—this is a gap, not an indication that the UI has been fully tested.

### 3. Make browser-to-backend routing work

Choose one of these arrangements before attempting authenticated API calls or Alpaca OAuth.

**Preferred: same-origin reverse proxy.** Serve the SPA and Accounts Service behind one origin, proxy `/api/**` to Accounts Service, and return the SPA entry file for browser routes such as `/dashboard`, `/watchlists/:id`, and `/settings/connections/*`. Set `VITE_ACCOUNTS_API_BASE_URL` to empty or to that shared origin. This also lets the backend’s current relative OAuth success redirect land in the SPA.

**Development: separate Vite and Accounts Service origins.** Configure Accounts Service CORS to allow the Vite origin and `Authorization`, then set `VITE_ACCOUNTS_API_BASE_URL` to its public base URL. You must additionally make the successful OAuth callback redirect to the Vite origin, or add a development reverse proxy. The current backend has no CORS configuration and returns a **relative** success redirect, so this arrangement does not work without backend/proxy work.

### 4. Manual smoke-test checklist

Run this against a non-production Keycloak realm and an Alpaca **paper** account.

1. Load `/login`; confirm the paper-trading disclaimer and no app-owned credential fields appear.
2. Log in (and, if enabled, register). Confirm the app reaches `/dashboard` and that browser requests to Accounts Service carry a bearer token.
3. Refresh the page and verify an existing SSO session is restored; log out and verify the user returns to `/login`.
4. Open Profile, update the display name, reload, and verify the persisted value.
5. Open Connections, start the OAuth flow, complete it with a paper account, and verify the success page obtains the returned connection.
6. Reconcile the connection; verify freshness/status feedback and dashboard values. Never retry a timed-out broker mutation automatically.
7. Create a watchlist, add and remove a ticker, toggle monitoring, and delete it. Verify changes in Alpaca paper as well as in the UI.
8. Check failure states deliberately: expired session (401), unauthorized user (403), invalid watchlist UUID, unavailable Accounts Service, and an Alpaca failure. Confirm no token, authorization code, client secret, or `secretReference` is shown.
9. Check the login, dashboard, connections, watchlist, profile, and OAuth-return routes at narrow mobile and desktop widths.

## Deployment requirements

- Serve `dist/` through an HTTP server; do not use a filesystem (`file://`) preview.
- Configure SPA history fallback for every client-side route, especially `/settings/connections/*` because it receives the OAuth return.
- Keep front end and Accounts Service same-origin where possible. Otherwise configure both CORS and a full front-end OAuth return URL.
- Use HTTPS outside local development and register the final HTTPS origin/redirect paths in Keycloak.
- Treat all Vite environment values as public build metadata. Keep server credentials only in backend/secret-manager configuration.

## Known integration gaps

These are code-level observations from the current repository, not merely future ideas. Resolve or explicitly version the contracts before relying on the UI.

| Priority | Gap                                                                                                                                                                                                                          | Effect / required decision                                                                                                                                                                    |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P0       | The front-end dashboard DTO expects `accounts` with `{ connection, portfolio, positionCount }`; Accounts Service currently returns `connections` with `{ connectionId, connectionStatus, account, positions }`.              | Per-account dashboard cards do not render correctly. Align the API/client contract and add contract tests.                                                                                    |
| P0       | The front-end watchlist DTO expects flat `id`, `entries`, counts, and status fields; Accounts Service list responses expose entity field names, while watchlist detail/create responses are nested `{ watchlist, entries }`. | Watchlist links/actions can use `undefined` IDs and detail rendering will not match the actual response. Add explicit API response DTOs/mappers on one side and test them.                    |
| P0       | Accounts Service exposes no CORS configuration, but the supplied env uses Vite (`:5173`) and API (`:8081`) as separate origins.                                                                                              | Browser requests will be blocked in direct local development until CORS or a dev/reverse proxy is configured.                                                                                 |
| P0       | The OAuth callback sends a relative redirect to `/settings/connections/success`; `FRONTEND_OAUTH_SUCCESS_URL` exists in backend properties but is not used.                                                                  | With the API on a separate origin, Alpaca returns to the API host rather than Vite. Use a same-origin gateway/proxy or change the backend to redirect to the configured front-end URL safely. |
| P0       | Repository defaults/examples conflict: front end uses Keycloak `:8085`, realm `trading-bot`, and API `:8081`; Accounts Service defaults to issuer realm `trading` on `:8080` and has no explicit server port.                | Establish a single local environment contract and commit safe templates/Compose/realm setup for it.                                                                                           |
| P1       | Portfolio, positions, and order client methods exist, but no portfolio-detail page or route is implemented; the dashboard link opens Connections instead.                                                                    | Build the detail page specified in `../frontend.md`, including freshness, account flags, positions, recent orders, and reconcile.                                                             |
| P1       | A typed `replaceWatchlist` method exists but the UI has no edit/replace-watchlist flow.                                                                                                                                      | Add rename/full-symbol-set editing with broker-mutation safeguards.                                                                                                                           |
| P1       | The API client uses `Number()` to format server monetary values.                                                                                                                                                             | Do not perform financial calculations in the browser; consider a decimal-safe display formatter to avoid precision surprises.                                                                 |
| P1       | `VITE_ACCOUNTS_API_BASE_URL` is not part of the startup configuration-error check.                                                                                                                                           | Show a clear configuration state for all four required settings, unless an intentional same-origin default is adopted.                                                                        |
| P1       | The current client relies on backend entity serialization and filters `secretReference` only after parsing.                                                                                                                  | Backend should return purpose-built public DTOs that never serialize sensitive fields; keep front-end redaction as defense in depth.                                                          |
| P2       | There are no front-end linting, formatting, unit, integration, accessibility, visual, or E2E tests.                                                                                                                          | Add a test stack (for example Vitest + Testing Library + MSW, then Playwright) and make build/test checks part of CI.                                                                         |
| P2       | API errors are decoded as JSON unconditionally and request cancellation/loading race handling is minimal.                                                                                                                    | Make error parsing resilient to HTML/text gateway failures and add cancellation/query-state handling as pages grow.                                                                           |

## Backlog for the front-end team

Work in this order so the interface is tested against a stable system rather than polished around mismatched data.

1. **Unblock local integration:** decide on same-origin proxy versus explicit CORS/frontend redirects; provide a reproducible Keycloak realm/client and local infrastructure setup; align ports/realm values.
2. **Stabilize the Accounts Service contract:** introduce public DTOs or update `src/api/types.ts` and mapping functions to match the server. Add API contract tests for dashboard, connection, and watchlist payloads.
3. **Complete the planned v1 screens:** portfolio/positions/orders detail, watchlist editing, and user-wide active-ticker summary if desired.
4. **Strengthen UX and resilience:** clearly handle `REAUTH_REQUIRED` and disconnected connections, stale data, API outages, refresh/retry behavior, accessibility of dialogs/forms, and loading/error states.
5. **Add quality gates:** formatter/linter, unit tests for API mapping and route guards, mocked component/integration tests, browser tests for Keycloak/OAuth test environments, and CI.
6. **Prepare production deployment:** SPA fallback, HTTPS, CSP/security headers, error monitoring that redacts tokens/secrets, and an environment-specific configuration/deployment guide.

## Useful references

- Front-end functional requirements and intended v1 scope: `../frontend.md`
- Accounts Service endpoint behavior: `../../account-service/API_DOCUMENTATION.md`
- Accounts Service security and local properties: `../../account-service/src/main/java/com/ai_got_this/account_service/config/SecurityConfig.java` and `../../account-service/src/main/resources/application.properties`
