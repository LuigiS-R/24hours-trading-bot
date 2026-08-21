import { keycloak } from "../auth/keycloak";

export class ApiError extends Error {
  constructor(public status: number, public payload: any) {
    super(`API Error ${status}`);
  }
}

export const errorText = (err: any): string => {
  if (!err) return "";
  if (typeof err === "string") return err;

  if (err instanceof ApiError || (err.status && err.payload)) {
    if (err.status === 403 || err.payload?.code === "FORBIDDEN") {
      return "You do not have permission for this action.";
    }
    if (
      err.status === 502 ||
      err.payload?.code?.startsWith("ALPACA_502") ||
      err.payload?.error?.startsWith("ALPACA_502")
    ) {
      return "Alpaca is temporarily unavailable. Do not retry automatically.";
    }
    if (err.payload?.message) return err.payload.message;
    if (err.payload?.error) return err.payload.error;
  }

  if (err.message) return err.message;
  return "An unexpected error occurred";
};

const sanitize = (data: any): any => {
  if (Array.isArray(data)) {
    return data.map(sanitize);
  }
  if (data !== null && typeof data === "object") {
    const clean: Record<string, any> = {};
    for (const key of Object.keys(data)) {
      if (key === "secretReference") {
        continue;
      }
      clean[key] = sanitize(data[key]);
    }
    return clean;
  }
  return data;
};

const getBaseUrl = () => {
  return import.meta.env.VITE_ACCOUNTS_API_BASE_URL || "http://localhost:8081";
};

const getHeaders = (token?: string, idempotencyKey?: string): Headers => {
  const headers = new Headers();
  headers.set("Content-Type", "application/json");
  const authToken = token || keycloak?.token;
  if (authToken) {
    headers.set("Authorization", `Bearer ${authToken}`);
  }
  if (idempotencyKey) {
    headers.set("Idempotency-Key", idempotencyKey);
  }
  return headers;
};

const handleResponse = async (res: Response) => {
  if (!res.ok) {
    let payload;
    try {
      payload = await res.json();
    } catch {
      payload = await res.text();
    }
    throw new ApiError(res.status, payload);
  }
  const json = await res.json();
  return sanitize(json);
};

export const accounts = {
  profile: async (token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/me`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  updateProfile: async (displayName: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/me`, {
      method: "PATCH",
      headers: getHeaders(token),
      body: JSON.stringify({ displayName }),
    });
    return handleResponse(res);
  },

  dashboard: async (token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/dashboard`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  connections: async (token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/connections`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  connection: async (id: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/connections/${id}`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  authorize: async (token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/connections/authorize`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  disconnect: async (id: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/connections/${id}`, {
      method: "DELETE",
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  reconcile: async (id: string, token?: string, idempotencyKey?: string) => {
    const key = idempotencyKey || (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : undefined);
    const res = await fetch(`${getBaseUrl()}/api/v1/connections/${id}/reconcile`, {
      method: "POST",
      headers: getHeaders(token, key),
    });
    return handleResponse(res);
  },

  watchlists: async (connId: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/connections/${connId}/watchlists`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  watchlist: async (id: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/watchlists/${id}`, {
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  createWatchlist: async (
    connId: string,
    name: string,
    symbols: string[],
    token?: string,
    idempotencyKey?: string
  ) => {
    const key = idempotencyKey || (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : undefined);
    const res = await fetch(`${getBaseUrl()}/api/v1/connections/${connId}/watchlists`, {
      method: "POST",
      headers: getHeaders(token, key),
      body: JSON.stringify({ name, symbols }),
    });
    return handleResponse(res);
  },

  addTicker: async (watchlistId: string, symbol: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/watchlists/${watchlistId}/tickers`, {
      method: "POST",
      headers: getHeaders(token),
      body: JSON.stringify({ symbol }),
    });
    return handleResponse(res);
  },

  toggleTicker: async (watchlistId: string, symbol: string, active: boolean, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/watchlists/${watchlistId}/tickers/${symbol}`, {
      method: "PATCH",
      headers: getHeaders(token),
      body: JSON.stringify({ active }),
    });
    return handleResponse(res);
  },

  removeTicker: async (watchlistId: string, symbol: string, token?: string) => {
    const res = await fetch(`${getBaseUrl()}/api/v1/watchlists/${watchlistId}/tickers/${symbol}`, {
      method: "DELETE",
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },

  deleteWatchlist: async (connIdOrId: string, maybeId?: string, token?: string) => {
    const url = maybeId
      ? `${getBaseUrl()}/api/v1/connections/${connIdOrId}/watchlists/${maybeId}`
      : `${getBaseUrl()}/api/v1/watchlists/${connIdOrId}`;
    const res = await fetch(url, {
      method: "DELETE",
      headers: getHeaders(token),
    });
    return handleResponse(res);
  },
};
