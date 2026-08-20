# Tradify Web Front End — Functional and Non-Functional Requirements

## 1. Purpose

Build a responsive single-page web application for Tradify, a **paper-trading-only** algorithmic trading platform.

The web application lets a Keycloak-authenticated user:

- Sign in, register, and sign out.
- Connect and disconnect their own Alpaca paper-trading account through OAuth.
- View local account, portfolio, and position projections.
- View recent order projections.
- Create and manage Alpaca-synchronized watchlists.
- Choose which watchlist entries are active for the trading bot.
- Reconcile account/watchlist data manually.
- View connection and data-freshness status.

The front end is a client of the implemented `account-service`. It must not call the AI services, RabbitMQ, PostgreSQL, Alpaca directly, or internal service endpoints.

## 2. Version-1 Scope

### In scope

- Keycloak login, registration, logout, and token refresh.
- Account Service public REST API integration.
- Alpaca paper-account OAuth connection flow.
- Dashboard, account connections, portfolio, positions, order history, watchlists, and profile.
- Responsive and accessible dark-theme interface.

### Explicitly out of scope

Do not implement:

- Live trading.
- Manual order placement, cancellation, replacement, or liquidation.
- Risk-limit configuration or kill-switch control.
- AI predictions, news sentiment, strategy signals, backtesting, model management, or training.
- Live market quotes, charts, WebSocket data feeds, or real-time order-event subscriptions.
- Notifications, email, SMS, or push notifications.
- Administrative/internal-service pages.
- Direct Alpaca API calls from the browser.
- Any browser display of OAuth tokens, OAuth authorization codes, secret references, API keys, or database information.

## 3. Required Technology and Project Structure

Create a new client application at:

```text
front-end/web/
```

Use:

- React 18.
- TypeScript with strict compiler settings.
- Vite.
- React Router.
- Keycloak JavaScript adapter.
- A small typed API client built on `fetch`.
- No server-side rendering is required.

The existing static login files under `front-end/frontEnd/` are legacy reference material. Do not extend them. The new React application replaces their user-facing purpose once deployed.

Suggested structure:

```text
front-end/web/
  src/
    api/
      accountsClient.ts
      types.ts
    auth/
      AuthProvider.tsx
      RequireAuth.tsx
      keycloak.ts
    components/
      AppShell.tsx
      ConnectionSelector.tsx
      DataFreshnessBadge.tsx
      EmptyState.tsx
      ErrorPanel.tsx
      LoadingState.tsx
      Money.tsx
      StatusBadge.tsx
    features/
      dashboard/
      connections/
      watchlists/
      profile/
    pages/
      LoginPage.tsx
      DashboardPage.tsx
      ConnectionsPage.tsx
      OAuthSuccessPage.tsx
      OAuthFailurePage.tsx
      WatchlistDetailPage.tsx
      ProfilePage.tsx
      NotFoundPage.tsx
    styles/
      tokens.css
      global.css
    App.tsx
    main.tsx
  .env.example
  README.md
  package.json
```

## 4. Configuration

The client reads only the following build-time environment variables:

```text
VITE_KEYCLOAK_URL
VITE_KEYCLOAK_REALM
VITE_KEYCLOAK_CLIENT_ID
VITE_ACCOUNTS_API_BASE_URL
```

Example development values:

```text
VITE_KEYCLOAK_URL=http://localhost:8085
VITE_KEYCLOAK_REALM=trading-bot
VITE_KEYCLOAK_CLIENT_ID=trading-bot-frontend-client
VITE_ACCOUNTS_API_BASE_URL=http://localhost:8081
```

Do not place Alpaca credentials, database credentials, Keycloak secrets, or any server secret in front-end environment variables.

## 5. Deployment Prerequisite

The Account Service’s Alpaca callback redirects to:

```text
/settings/connections/success?connectionId={uuid}
```

Therefore, production deployment must satisfy one of these conditions:

1. Preferably, the API gateway serves the React application and Accounts Service under the same origin:
   - `/api/**` routes to Accounts Service.
   - `/settings/connections/**` serves the React SPA.
   - All other browser routes serve the SPA entry point.

2. If the front end and Accounts Service use separate origins, Accounts Service must be changed to redirect to the front-end origin. The current backend property for a front-end OAuth URL is not yet used.

Without one of these deployment arrangements, a successful Alpaca OAuth callback cannot reliably return the user to the React application.

## 6. Authentication Requirements

### 6.1 Keycloak integration

Use the Keycloak JavaScript adapter.

On application startup:

1. Initialize Keycloak without forcing login.
2. If authenticated, render the protected application.
3. If not authenticated, render the Login page.
4. Before every Account Service request, call `keycloak.updateToken(30)`.
5. Attach the current access token as:

```http
Authorization: Bearer {keycloak-access-token}
```

6. If refresh fails or an authenticated request receives HTTP `401`, clear local application state and redirect to Login.
7. If an API request receives HTTP `403`, show an access-denied error; do not retry automatically.

The application must not manually store a Keycloak access token in `localStorage`, `sessionStorage`, IndexedDB, a URL, analytics data, or logs.

### 6.2 Login page

Route: `/login`

The page must provide:

- Tradify logo/name.
- Short paper-trading disclaimer: “Paper trading only. No real-money orders are placed.”
- “Log in” button that calls `keycloak.login()`.
- “Create account” button that calls `keycloak.register()`.
- Clear loading/error state during Keycloak initialization.
- No email/password fields submitted directly to the application. Keycloak owns credentials.

If the user is already authenticated and visits `/login`, redirect to `/dashboard`.

### 6.3 Logout

The authenticated application shell must provide a Logout action.

On click:

1. Clear in-memory UI state.
2. Call `keycloak.logout()` with the application login route as the redirect target.
3. Do not call Account Service to log out.

## 7. Navigation and Routes

| Route | Access | Purpose |
|---|---|---|
| `/login` | Public | Keycloak login and registration entry point. |
| `/dashboard` | Authenticated | Portfolio/dashboard overview. |
| `/connections` | Authenticated | Manage Alpaca paper-account connections. |
| `/settings/connections/success` | Authenticated | OAuth-success confirmation. |
| `/settings/connections/failure` | Public | OAuth-failure explanation and return to login/dashboard. |
| `/watchlists/:watchlistId` | Authenticated | View and manage one watchlist. |
| `/profile` | Authenticated | View and update display name. |
| `*` | Public/authenticated | Not-found page with an appropriate return link. |

Authenticated routes must use a `RequireAuth` guard. An unauthenticated visitor is redirected to `/login`.

The main navigation contains:

- Dashboard
- Connections
- Watchlists
- Profile
- Logout

Watchlists may appear as a navigation sub-menu or as a section within Dashboard and Connections.

## 8. Typed Accounts Service API Contract

All URLs below are relative to `VITE_ACCOUNTS_API_BASE_URL`.

### 8.1 User profile

| Method | Path | Front-end use |
|---|---|---|
| `GET` | `/api/v1/me` | Load current user profile. |
| `PATCH` | `/api/v1/me` | Update display name. |

Update request:

```json
{
  "displayName": "Ada Trader"
}
```

### 8.2 Alpaca OAuth connections

| Method | Path | Front-end use |
|---|---|---|
| `POST` | `/api/v1/alpaca/connections/authorize` | Start Alpaca paper OAuth. |
| `GET` | `/api/v1/alpaca/connections` | List current user’s connections. |
| `GET` | `/api/v1/alpaca/connections/{connectionId}` | Load one connection. |
| `POST` | `/api/v1/alpaca/connections/{connectionId}/reconcile` | Manually synchronize account/watchlists. |
| `DELETE` | `/api/v1/alpaca/connections/{connectionId}` | Disconnect an account. |

OAuth-start response:

```json
{
  "authorizationUrl": "https://app.alpaca.markets/oauth/authorize?...",
  "expiresAt": "2026-08-16T00:00:00Z"
}
```

When this response is received, assign:

```ts
window.location.assign(authorizationUrl);
```

Do not open the URL in an iframe, popup, background tab, or embedded WebView.

Connection responses currently include a `secretReference` field. The UI must deliberately omit this field from rendering, application state shown in developer-facing UI, logs, and error messages.

Connection status values:

| Status | User-facing meaning | Available UI actions |
|---|---|---|
| `PENDING` | Connection setup is in progress. | Refresh status. |
| `CONNECTED` | Paper account is connected. | Reconcile, manage watchlists, disconnect. |
| `REAUTH_REQUIRED` | Alpaca authorization is no longer usable. | Reconnect through OAuth; disconnect. |
| `ERROR` | Synchronization failed. | Reconcile, reconnect, disconnect. |
| `DISCONNECTED` | Connection was removed. | Hide from active views. |

### 8.3 Dashboard and portfolio

| Method | Path | Front-end use |
|---|---|---|
| `GET` | `/api/v1/dashboard` | Load top-level dashboard. |
| `GET` | `/api/v1/alpaca/connections/{connectionId}/portfolio` | Load one account’s portfolio and positions. |
| `GET` | `/api/v1/alpaca/connections/{connectionId}/positions` | Load position list. |
| `GET` | `/api/v1/alpaca/connections/{connectionId}/orders` | Load up to 20 recent order projections. |

The client must not calculate totals, P&L, buying power, or risk eligibility. It displays server-provided values only.

### 8.4 Watchlists

| Method | Path | Front-end use |
|---|---|---|
| `GET` | `/api/v1/alpaca/connections/{connectionId}/watchlists` | List watchlists for a connection. |
| `POST` | `/api/v1/alpaca/connections/{connectionId}/watchlists` | Create watchlist. |
| `GET` | `/api/v1/watchlists/{watchlistId}` | Load detail. |
| `PUT` | `/api/v1/watchlists/{watchlistId}` | Replace name and complete ticker set. |
| `POST` | `/api/v1/watchlists/{watchlistId}/entries` | Add ticker. |
| `DELETE` | `/api/v1/watchlists/{watchlistId}/entries/{ticker}` | Remove ticker. |
| `PATCH` | `/api/v1/watchlists/{watchlistId}/entries/{ticker}` | Toggle local active status. |
| `DELETE` | `/api/v1/watchlists/{watchlistId}` | Delete watchlist. |
| `GET` | `/api/v1/watchlists/active-tickers` | Display a user-wide active ticker summary if needed. |

The following requests require:

```http
Idempotency-Key: {UUID}
```

- Create watchlist
- Replace watchlist
- Add ticker
- Remove ticker
- Delete watchlist

Generate the UUID using `crypto.randomUUID()` once per user action. Preserve it only while that one request is in flight. The current backend requires the header but does not yet replay duplicate requests safely, so the client must disable the action button while the request is pending and must not automatically retry a timed-out broker mutation.

## 9. Functional Requirements

### FR-1: Application shell

The authenticated application displays:

- Product name: Tradify.
- A clear `PAPER TRADING` badge at all times.
- Current user display name.
- Navigation described in Section 7.
- Selected Alpaca connection selector when one or more connections exist.
- Connection status and freshness badges.

The application must never use wording that suggests real-money trading.

### FR-2: Connection management

The Connections page must:

1. Load all user-owned Alpaca connections.
2. Display connection status, Alpaca account ID, paper environment, last verification time, and safe error code.
3. Provide a “Connect Alpaca Paper Account” button.
4. Disable the connect button only while the OAuth-start request is pending.
5. Redirect the browser to the returned Alpaca authorization URL.
6. Provide a “Reconcile now” action for `CONNECTED`, `PENDING`, or `ERROR` connections.
7. Show reconciliation progress and reload connection/dashboard/watchlist data after success.
8. Provide a destructive “Disconnect” action requiring a confirmation dialog naming the affected paper account.
9. Never display `secretReference`.

### FR-3: OAuth completion pages

#### Success page

Route: `/settings/connections/success?connectionId={uuid}`

The page must:

1. Read and validate `connectionId` as a UUID.
2. Load the connection using `GET /api/v1/alpaca/connections/{connectionId}`.
3. Display a success message, `PAPER` environment, and account identifier.
4. Offer buttons for “View dashboard” and “Manage watchlists.”
5. Show an error state if the connection cannot be loaded.

#### Failure page

Route: `/settings/connections/failure`

The page must:

- Explain that the Alpaca connection did not complete.
- Never display raw OAuth errors, tokens, codes, or secrets.
- Provide “Try again” and “Return to dashboard” actions.

### FR-4: Dashboard

The Dashboard page must call `GET /api/v1/dashboard` when opened and after a successful reconciliation.

For each connected account, display:

- Connection status.
- Cash.
- Buying power.
- Equity.
- Portfolio value.
- Last synchronization time.
- Freshness badge.
- Position count.
- Link to detailed portfolio.
- Link to connection/watchlists.

Display aggregate total cash, total equity, and total portfolio value only when dashboard freshness is `FRESH`.

Freshness behavior:

| Value | UI behavior |
|---|---|
| `FRESH` | Show normal values. |
| `STALE` | Show values with warning badge and “Data may be out of date.” |
| `UNAVAILABLE` | Do not show aggregated financial totals as reliable; show an empty/error state and a Reconcile action. |

If there are no connections, show an onboarding empty state with “Connect Alpaca Paper Account.”

### FR-5: Portfolio detail

For a selected connection, show:

- Account summary: cash, buying power, equity, portfolio value, account flags, and last synchronization.
- Freshness status.
- Positions table.
- Recent order projections table.
- Manual Reconcile action.

Positions table columns:

- Symbol
- Side
- Quantity
- Average entry price
- Current price
- Market value
- Unrealized P&L
- Updated time

Order table columns:

- Symbol
- Side
- Quantity
- Filled quantity
- Order type
- Status
- Average fill price
- Updated time

The UI must show a clear empty state when no positions or no orders are returned. It must label orders as “local order projections” because current backend event consumption is not implemented.

### FR-6: Watchlist list and detail

The UI must support watchlists only under a selected connected Alpaca paper account.

#### Watchlist list

Display:

- Watchlist name.
- Number of entries.
- Count of active entries.
- Synchronization status.
- Last synchronization error code if present.
- Link to detail.
- “Create watchlist” action.

#### Create watchlist

The modal/page must collect:

- Required name.
- Optional initial symbols, entered as comma-separated symbols or removable chips.

Before submission:

- Trim whitespace.
- Convert tickers to uppercase.
- Reject malformed symbols client-side using `[A-Z][A-Z.]{0,15}`.
- Reject duplicate symbols client-side.
- Require a non-empty trimmed name.

On submission:

- Call the create endpoint with a UUID `Idempotency-Key`.
- Disable all submit/cancel-mutating controls while pending.
- On success, navigate to the new watchlist detail.
- On failure, preserve user-entered form content and show mapped error text.

#### Watchlist detail

Display:

- Watchlist name.
- `SYNCED`, `PENDING`, `SYNC_FAILED`, or `PENDING_DELETE` status.
- Ticker entries.
- Active/inactive toggle.
- Add ticker form.
- Remove ticker action.
- Edit watchlist name/full symbol set action.
- Delete watchlist action.

The active toggle is local bot-subscription intent only. Its UI copy must state: “Turning off a ticker stops bot monitoring signals; it does not sell holdings or cancel orders.”

Removing a ticker or deleting a watchlist requires confirmation because the operation changes Alpaca’s broker watchlist.

### FR-7: Profile

The Profile page must:

- Load `GET /api/v1/me`.
- Display email as read-only.
- Allow editing display name.
- Submit `PATCH /api/v1/me`.
- Show confirmation after success.
- Preserve form values and show an error after failure.

### FR-8: API error handling

For Account Service errors in this shape:

```json
{
  "code": "ALPACA_REAUTH_REQUIRED",
  "message": "Reconnect your Alpaca paper account to continue.",
  "correlationId": "uuid",
  "timestamp": "2026-08-16T00:00:00Z"
}
```

map errors as follows:

| Error code/status | User-facing behavior |
|---|---|
| `401` | Redirect to Login. |
| `403` | Show “You do not have permission for this action.” |
| `404 CONNECTION_NOT_FOUND` | Return to Connections with a notification. |
| `404 WATCHLIST_NOT_FOUND` | Return to Watchlists with a notification. |
| `409 ALPACA_REAUTH_REQUIRED` | Show reconnect banner and Connect action. |
| `409 WATCHLIST_ENTRY_EXISTS` | Show inline duplicate-ticker validation. |
| `409 WATCHLIST_NAME_CONFLICT` | Show inline duplicate-name validation. |
| `400 INVALID_TICKER` | Show inline ticker validation. |
| `502 ALPACA_*` | Show “Alpaca is temporarily unavailable. Do not retry automatically; reconcile or try again later.” |
| `500` | Show a generic recoverable error with correlation ID when present. |

Never show raw server stack traces, OAuth codes, token values, secret references, or raw broker response bodies.

## 10. User Interface Requirements

### Visual language

Use a dark, restrained trading-dashboard visual style consistent with the existing login artwork:

- Background: near-black.
- Surfaces: dark charcoal.
- Accent: indigo/violet.
- Positive status: accessible green.
- Warning/stale: accessible amber.
- Error/disconnected: accessible red.
- Text: high-contrast white/gray.

Use existing `front-end/UIX/` imagery only on Login. Dashboard screens must prioritize legible financial data over decorative imagery.

### Responsive behavior

| Viewport | Required behavior |
|---|---|
| Desktop, 1024px+ | Persistent sidebar/top navigation; multi-column dashboard cards and tables. |
| Tablet, 768–1023px | Collapsible navigation; two-column cards where space permits. |
| Mobile, below 768px | Drawer navigation; one-column cards; horizontally scrollable tables with visible column labels. |

Do not hide account status, freshness, paper-trading badge, or destructive-action confirmations on mobile.

### Accessibility

The application must meet WCAG 2.2 AA for the implemented UI:

- Keyboard-operable navigation, controls, modals, and toggles.
- Visible focus indicators.
- Semantic headings, landmarks, tables, labels, and buttons.
- Form errors associated with fields through accessible descriptions.
- Color is never the only status indicator.
- Minimum 4.5:1 normal-text contrast ratio.
- Toasts and asynchronous errors announced through an `aria-live` region.
- Confirmation dialogs trap focus and return focus to the initiating control when closed.

## 11. Non-Functional Requirements

### NFR-1: Security

- Use HTTPS in non-local environments.
- Send all Account Service requests with Keycloak bearer authentication.
- Keep tokens in Keycloak-managed memory only.
- Never log API authorization headers or response fields that contain `secretReference`.
- Do not use `dangerouslySetInnerHTML`.
- Validate all route UUIDs before making a request.
- Encode dynamic user values through normal React rendering.
- Use a restrictive Content Security Policy compatible with Keycloak and Alpaca redirects.

### NFR-2: Reliability

- Every view has loading, empty, success, error, and retry/reconcile states.
- Do not automatically retry broker-mutating requests.
- Do not show successful mutation feedback until the Account Service returns success.
- After a successful watchlist mutation, reload the affected watchlist from the server.
- After a successful reconciliation, reload dashboard, connection, and watchlist data.
- Abort obsolete in-flight read requests when navigating away where practical.

### NFR-3: Performance

- Initial authenticated shell must render within two seconds on a typical broadband connection, excluding third-party Keycloak/Alpaca redirects.
- Avoid duplicate dashboard requests during one route transition.
- Cache read-only data only in memory and invalidate it after mutation/reconciliation.
- Use code splitting for route-level pages.
- Do not load background images on dashboard routes.
- Avoid client-side financial calculations; format server values only.

### NFR-4: Data integrity

- Treat Account Service as the browser data authority.
- Preserve `BigDecimal` values as received; format them for display without performing financial calculations.
- Display all financial values in USD with two visible decimal places.
- Use `Intl.DateTimeFormat` for the user’s locale while preserving UTC server values internally.
- Never combine data across connections unless it comes from the dashboard aggregate response.
- Display `PAPER` for every connected account and portfolio.

### NFR-5: Observability

- Send only sanitized client-side errors to the configured error-reporting mechanism.
- Include API correlation IDs in user-visible technical details and sanitized logs.
- Record front-end telemetry only for non-sensitive events:
  - page view,
  - OAuth authorization started/completed/failed,
  - reconciliation started/completed/failed,
  - watchlist mutation success/failure.
- Never record ticker lists, email addresses, JWTs, account identifiers, OAuth URLs, authorization codes, or secret references in third-party analytics without explicit privacy approval.

### NFR-6: Testing

Implement:

- Unit tests for API-client error mapping, money/date formatting, ticker validation, and route guards.
- Component tests for Login, Dashboard states, connection actions, and watchlist forms.
- Integration tests with mocked Account Service responses for:
  - OAuth authorization start,
  - connection reconciliation,
  - stale/unavailable dashboard data,
  - watchlist create/add/remove/delete,
  - `ALPACA_REAUTH_REQUIRED`,
  - validation and broker failures.
- End-to-end tests using mocked Keycloak and Account Service behavior for the primary user flows.

## 12. Acceptance Criteria

1. An unauthenticated user can only access Login and OAuth-failure pages.
2. An authenticated user can connect an Alpaca paper account without the browser ever receiving an OAuth token.
3. The OAuth success page loads the connected account from `connectionId`.
4. The dashboard accurately displays Account Service data and clearly marks stale/unavailable projections.
5. No UI path creates or submits a trade.
6. A user can create, rename/replace, add to, remove from, activate/deactivate, and delete a watchlist through the implemented API.
7. Watchlist broker mutations always include an `Idempotency-Key`.
8. A client-side timeout during a watchlist mutation does not cause an automatic duplicate retry.
9. A user cannot view or interact with `secretReference`.
10. All pages work on desktop and mobile, are keyboard accessible, and maintain the persistent `PAPER TRADING` indication.
11. The application builds successfully with `npm run build`.
12. The README explains local setup, environment variables, Keycloak setup assumptions, routing/deployment prerequisite, and test commands.
## Running the Automated Frontend Tests

To run the Vitest + Happy-DOM test suite:
```bash
cd frontend/web
npm test
```

### Test Options
```bash
# Run tests in interactive watch mode
npx vitest

# Run tests with code coverage report
npx vitest run --coverage
```

### Test Coverage Breakdown
- **API Client Suite (`src/api/accountsClient.test.ts`):** Verifies Bearer token headers, `Idempotency-Key` headers on mutating requests, and sanitized error mapping (403 / 502).
- **Component Suite (`src/pages/Pages.test.tsx`):** Verifies login disclaimers, empty dashboard projections, and OAuth failure fallback state rendering.
