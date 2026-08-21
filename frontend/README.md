# Tradify Web Frontend

Single-page web client for the Tradify algorithmic paper-trading platform built with React, Vite, TypeScript, and Keycloak authentication.

---

## Running the Test Suite

To run the automated Vitest test suite:

```bash
cd frontend/web
npm test
```

### Additional Test Commands

```bash
# Run tests in interactive watch mode
npx vitest

# Run tests with code coverage report
npx vitest run --coverage
```

### Test Coverage Summary
- **API Client Suite (`src/api/accountsClient.test.ts`):**
  - Injects Keycloak Bearer tokens automatically on outgoing requests.
  - Attaches `Idempotency-Key` headers on mutating requests.
  - Sanitizes sensitive payloads by stripping `secretReference`.
  - Maps 403 (Permission) and 502 (Alpaca Broker Timeout) to user-friendly messages.
- **Component Suite (`src/pages/Pages.test.tsx`):**
  - Renders login disclaimer and authentication triggers.
  - Verifies zero-state portfolio and connection projections.
  - Verifies OAuth fallback error boundaries.
