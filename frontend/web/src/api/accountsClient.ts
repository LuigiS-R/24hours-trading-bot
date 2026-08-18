import { keycloak } from '../auth/keycloak';
import type { ApiErrorShape, Connection, Dashboard, Order, Portfolio, Position, Profile, Watchlist } from './types';

const base = import.meta.env.VITE_ACCOUNTS_API_BASE_URL || '';
export class ApiError extends Error { constructor(public status: number, public body: ApiErrorShape = {}) { super(body.message || 'Something went wrong. Please try again.'); } }
const clean = (value: unknown): unknown => Array.isArray(value) ? value.map(clean) : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value as Record<string, unknown>).filter(([k]) => k !== 'secretReference').map(([k, v]) => [k, clean(v)])) : value;
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  try { await keycloak.updateToken(30); } catch { keycloak.clearToken(); window.location.assign('/login'); throw new ApiError(401); }
  const headers = new Headers(init.headers); headers.set('Authorization', `Bearer ${keycloak.token ?? ''}`); if (init.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(`${base}${path}`, { ...init, headers });
  const body = response.status === 204 ? undefined : await response.json().catch(() => ({}));
  if (!response.ok) { if (response.status === 401) { keycloak.clearToken(); window.location.assign('/login'); } throw new ApiError(response.status, clean(body) as ApiErrorShape); }
  return clean(body) as T;
}
const idempotency = () => ({ 'Idempotency-Key': crypto.randomUUID() });
export const accounts = {
  profile: () => request<Profile>('/api/v1/me'), updateProfile: (displayName: string) => request<Profile>('/api/v1/me', { method: 'PATCH', body: JSON.stringify({ displayName }) }),
  dashboard: () => request<Dashboard>('/api/v1/dashboard'), connections: () => request<Connection[]>('/api/v1/alpaca/connections'), connection: (id: string) => request<Connection>(`/api/v1/alpaca/connections/${id}`),
  authorize: () => request<{ authorizationUrl: string }>('/api/v1/alpaca/connections/authorize', { method: 'POST' }), reconcile: (id: string) => request<void>(`/api/v1/alpaca/connections/${id}/reconcile`, { method: 'POST' }), disconnect: (id: string) => request<void>(`/api/v1/alpaca/connections/${id}`, { method: 'DELETE' }),
  portfolio: (id: string) => request<Portfolio>(`/api/v1/alpaca/connections/${id}/portfolio`), positions: (id: string) => request<Position[]>(`/api/v1/alpaca/connections/${id}/positions`), orders: (id: string) => request<Order[]>(`/api/v1/alpaca/connections/${id}/orders`),
  watchlists: (id: string) => request<Watchlist[]>(`/api/v1/alpaca/connections/${id}/watchlists`), watchlist: (id: string) => request<Watchlist>(`/api/v1/watchlists/${id}`),
  createWatchlist: (id: string, name: string, symbols: string[]) => request<Watchlist>(`/api/v1/alpaca/connections/${id}/watchlists`, { method: 'POST', headers: idempotency(), body: JSON.stringify({ name, symbols }) }),
  replaceWatchlist: (id: string, name: string, symbols: string[]) => request<Watchlist>(`/api/v1/watchlists/${id}`, { method: 'PUT', headers: idempotency(), body: JSON.stringify({ name, symbols }) }),
  addTicker: (id: string, ticker: string) => request<Watchlist>(`/api/v1/watchlists/${id}/entries`, { method: 'POST', headers: idempotency(), body: JSON.stringify({ ticker }) }),
  removeTicker: (id: string, ticker: string) => request<void>(`/api/v1/watchlists/${id}/entries/${encodeURIComponent(ticker)}`, { method: 'DELETE', headers: idempotency() }),
  toggleTicker: (id: string, ticker: string, active: boolean) => request<Watchlist>(`/api/v1/watchlists/${id}/entries/${encodeURIComponent(ticker)}`, { method: 'PATCH', body: JSON.stringify({ active }) }),
  deleteWatchlist: (id: string) => request<void>(`/api/v1/watchlists/${id}`, { method: 'DELETE', headers: idempotency() })
};
export function errorText(error: unknown) { const e = error as ApiError; if (e.status === 403) return 'You do not have permission for this action.'; if (e.status === 502 || e.body?.code?.startsWith('ALPACA_')) return 'Alpaca is temporarily unavailable. Do not retry automatically; reconcile or try again later.'; return `${e.message}${e.body?.correlationId ? ` (Reference: ${e.body.correlationId})` : ''}`; }
