import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { accounts, errorText, ApiError } from './accountsClient';
import { keycloak } from '../auth/keycloak';

vi.mock('../auth/keycloak', () => ({
  keycloak: {
    token: 'mock-test-jwt-token',
    updateToken: vi.fn().mockResolvedValue(true),
    clearToken: vi.fn(),
  },
}));

describe('accountsClient Unit & Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.fetch = vi.fn();
    Object.defineProperty(globalThis, 'crypto', {
      value: {
        randomUUID: () => 'mock-uuid-1234',
      },
      writable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches user profile successfully', async () => {
    const mockProfile = { id: 'u1', email: 'ada@example.com', displayName: 'Ada Lovelace' };
    (globalThis.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(mockProfile),
    });

    const result = await accounts.profile();
    expect(result).toEqual(mockProfile);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/me'),
      expect.objectContaining({
        headers: expect.any(Headers),
      })
    );
  });

  it('omits secretReference from API response payloads', async () => {
    const rawConnection = {
      id: 'conn-1',
      status: 'CONNECTED',
      accountNumber: 'PA12345',
      secretReference: 'SUPER_SECRET_TOKEN_DO_NOT_LEAK',
    };
    (globalThis.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(rawConnection),
    });

    const result = await accounts.connection('conn-1');
    expect(result).toEqual({
      id: 'conn-1',
      status: 'CONNECTED',
      accountNumber: 'PA12345',
    });
    expect((result as any).secretReference).toBeUndefined();
  });

  it('attaches Idempotency-Key header on mutating watchlist requests', async () => {
    (globalThis.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({
          id: 'wl-1',
          name: 'Tech',
          symbols: ['AAPL'],
        }),
    });

    await accounts.createWatchlist('conn-1', 'Tech', ['AAPL']);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    const [, init] = (globalThis.fetch as any).mock.calls[0];
    const headers = init.headers as Headers;
    expect(headers.get('Idempotency-Key')).toBe('mock-uuid-1234');
    expect(headers.get('Authorization')).toBe('Bearer mock-test-jwt-token');
  });

  it('maps 403 status code to permission error message', () => {
    const err = new ApiError(403, { code: 'FORBIDDEN', message: 'Forbidden' });
    expect(errorText(err)).toBe('You do not have permission for this action.');
  });

  it('maps Alpaca 502 error to safe retry advice without raw broker details', () => {
    const err = new ApiError(502, { code: 'ALPACA_502_BAD_GATEWAY', correlationId: 'corr-999' });
    expect(errorText(err)).toContain('Alpaca is temporarily unavailable. Do not retry automatically');
  });
});
