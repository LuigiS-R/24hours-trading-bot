import { keycloak } from "../auth/keycloak";

export class ApiError extends Error {
  constructor(
    public status: number,
    public payload: any
  ) {
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
      err.payload?.code?.startsWith("ALPACA_") ||
      err.payload?.error?.startsWith("ALPACA_")
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
  return (
    import.meta.env.VITE_ACCOUNTS_API_BASE_URL ||
    "http://localhost:8081"
  );
};

const getHeaders = (
  token?: string,
  idempotencyKey?: string
): Headers => {
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
    let payload: any;

    try {
      payload = await res.json();
    } catch {
      payload = await res.text();
    }

    throw new ApiError(res.status, payload);
  }

  // DELETE endpoints commonly return 204 No Content.
  if (res.status === 204) {
    return null;
  }

  const text = await res.text();

  if (!text) {
    return null;
  }

  try {
    return sanitize(JSON.parse(text));
  } catch {
    return sanitize(text);
  }
};

const newIdempotencyKey = () => {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return undefined;
};

export const accounts = {
  profile: async (token?: string) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/me`,
      {
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  updateProfile: async (
    displayName: string,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/me`,
      {
        method: "PATCH",
        headers: getHeaders(token),
        body: JSON.stringify({ displayName }),
      }
    );

    return handleResponse(res);
  },

  dashboard: async (token?: string) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/dashboard`,
      {
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // GET /api/v1/alpaca/connections
  connections: async (token?: string) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections`,
      {
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // GET /api/v1/alpaca/connections/{id}
  connection: async (
    id: string,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections/${id}`,
      {
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // POST /api/v1/alpaca/connections/authorize
  authorize: async (token?: string) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections/authorize`,
      {
        method: "POST",
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // DELETE /api/v1/alpaca/connections/{id}
  disconnect: async (
    id: string,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections/${id}`,
      {
        method: "DELETE",
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // POST /api/v1/alpaca/connections/{id}/reconcile
  reconcile: async (
    id: string,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections/${id}/reconcile`,
      {
        method: "POST",
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // GET /api/v1/alpaca/connections/{connectionId}/watchlists
  watchlists: async (
    connId: string,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections/${connId}/watchlists`,
      {
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // GET /api/v1/watchlists/{watchlistId}
  watchlist: async (
    id: string,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/watchlists/${id}`,
      {
        headers: getHeaders(token),
      }
    );

    return handleResponse(res);
  },

  // POST /api/v1/alpaca/connections/{connectionId}/watchlists
  createWatchlist: async (
    connId: string,
    name: string,
    symbols: string[],
    token?: string,
    idempotencyKey?: string
  ) => {
    const key = idempotencyKey || newIdempotencyKey();

    const res = await fetch(
      `${getBaseUrl()}/api/v1/alpaca/connections/${connId}/watchlists`,
      {
        method: "POST",
        headers: getHeaders(token, key),
        body: JSON.stringify({ name, symbols }),
      }
    );

    return handleResponse(res);
  },

  // PUT /api/v1/watchlists/{watchlistId}
  replaceWatchlist: async (
    watchlistId: string,
    name: string,
    symbols: string[],
    token?: string,
    idempotencyKey?: string
  ) => {
    const key = idempotencyKey || newIdempotencyKey();

    const res = await fetch(
      `${getBaseUrl()}/api/v1/watchlists/${watchlistId}`,
      {
        method: "PUT",
        headers: getHeaders(token, key),
        body: JSON.stringify({ name, symbols }),
      }
    );

    return handleResponse(res);
  },

  // POST /api/v1/watchlists/{watchlistId}/entries
  addTicker: async (
    watchlistId: string,
    symbol: string,
    token?: string,
    idempotencyKey?: string
  ) => {
    const key = idempotencyKey || newIdempotencyKey();

    const res = await fetch(
      `${getBaseUrl()}/api/v1/watchlists/${watchlistId}/entries`,
      {
        method: "POST",
        headers: getHeaders(token, key),
        body: JSON.stringify({ ticker: symbol }),
      }
    );

    return handleResponse(res);
  },

  // PATCH /api/v1/watchlists/{watchlistId}/entries/{ticker}
  toggleTicker: async (
    watchlistId: string,
    symbol: string,
    active: boolean,
    token?: string
  ) => {
    const res = await fetch(
      `${getBaseUrl()}/api/v1/watchlists/${watchlistId}/entries/${encodeURIComponent(symbol)}`,
      {
        method: "PATCH",
        headers: getHeaders(token),
        body: JSON.stringify({ active }),
      }
    );

    return handleResponse(res);
  },

  // DELETE /api/v1/watchlists/{watchlistId}/entries/{ticker}
  removeTicker: async (
    watchlistId: string,
    symbol: string,
    token?: string,
    idempotencyKey?: string
  ) => {
    const key = idempotencyKey || newIdempotencyKey();

    const res = await fetch(
      `${getBaseUrl()}/api/v1/watchlists/${watchlistId}/entries/${encodeURIComponent(symbol)}`,
      {
        method: "DELETE",
        headers: getHeaders(token, key),
      }
    );

    return handleResponse(res);
  },

  // DELETE /api/v1/watchlists/{watchlistId}
  deleteWatchlist: async (
    watchlistId: string,
    token?: string,
    idempotencyKey?: string
  ) => {
    const key = idempotencyKey || newIdempotencyKey();

    const res = await fetch(
      `${getBaseUrl()}/api/v1/watchlists/${watchlistId}`,
      {
        method: "DELETE",
        headers: getHeaders(token, key),
      }
    );

    return handleResponse(res);
  },
};