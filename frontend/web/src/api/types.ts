export type Freshness = "FRESH" | "STALE" | "UNAVAILABLE";
export type ConnectionStatus =
  | "PENDING"
  | "CONNECTED"
  | "REAUTH_REQUIRED"
  | "ERROR"
  | "DISCONNECTED";
export interface ApiErrorShape {
  code?: string;
  message?: string;
  correlationId?: string;
}
export interface Profile {
  email: string;
  displayName: string;
}
export interface Connection {
  id: string;
  connectionId?: string;
  status: ConnectionStatus;
  accountId?: string;
  alpacaAccountId?: string;
  environment?: string;
  lastVerifiedAt?: string;
  lastSynchronizedAt?: string;
  freshness?: Freshness;
  errorCode?: string;
}
export interface Portfolio {
  cash?: string;
  buyingPower?: string;
  equity?: string;
  portfolioValue?: string;
  accountFlags?: string[];
  lastSynchronizedAt?: string;
  freshness?: Freshness;
}
export interface Position {
  symbol: string;
  side?: string;
  quantity?: string;
  avgEntryPrice?: string;
  currentPrice?: string;
  marketValue?: string;
  unrealizedPl?: string;
  updatedAt?: string;
}
export interface Order {
  symbol?: string;
  side?: string;
  quantity?: string;
  filledQuantity?: string;
  orderType?: string;
  status?: string;
  avgFillPrice?: string;
  updatedAt?: string;
}
export interface WatchlistEntry {
  ticker: string;
  symbol?: string;
  active: boolean;
}
export interface Watchlist {
  id: string;
  name: string;
  entries?: WatchlistEntry[];
  entryCount?: number;
  activeEntryCount?: number;
  status?: string;
  lastSynchronizedAt?: string;
  lastSynchronizationErrorCode?: string;
  connectionId?: string;
}
export interface DashboardAccount {
  connection: Connection;
  portfolio?: Portfolio;
  positionCount?: number;
}
export interface Dashboard {
  freshness: Freshness;
  totalCash?: string;
  totalEquity?: string;
  totalPortfolioValue?: string;
  accounts?: DashboardAccount[];
}
