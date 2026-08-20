import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import {
  LoginPage,
  DashboardPage,
  OAuthFailurePage,
} from './Pages';
import { accounts } from '../api/accountsClient';

const mockKeycloak = {
  authenticated: false,
  token: 'mock-token',
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  updateToken: vi.fn().mockResolvedValue(true),
  clearToken: vi.fn(),
  tokenParsed: { preferred_username: 'TraderBotUser' },
};

vi.mock('@react-keycloak/web', () => ({
  useKeycloak: () => ({
    initialized: true,
    keycloak: mockKeycloak,
  }),
}));

vi.mock('../auth/keycloak', () => ({
  keycloak: mockKeycloak,
}));

vi.mock('../api/accountsClient', () => ({
  accounts: {
    dashboard: vi.fn(),
    connection: vi.fn(),
    connections: vi.fn(),
    reconcile: vi.fn(),
    watchlists: vi.fn(),
  },
  errorText: vi.fn((err: any) => err.message || 'Error occurred'),
}));

describe('Frontend Pages Component Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockKeycloak.authenticated = false;
    (accounts.connections as any).mockResolvedValue([]);
  });

  it('renders LoginPage with paper trading disclaimer and auth triggers', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/Paper trading only/i)).toBeInTheDocument();
    
    const loginBtn = screen.getByRole('button', { name: /log in/i });
    fireEvent.click(loginBtn);
    expect(mockKeycloak.login).toHaveBeenCalledTimes(1);

    const registerBtn = screen.getByRole('button', { name: /create account/i });
    fireEvent.click(registerBtn);
    expect(mockKeycloak.register).toHaveBeenCalledTimes(1);
  });

  it('renders OAuthFailurePage with retry action and no raw secrets', () => {
    render(
      <MemoryRouter>
        <OAuthFailurePage />
      </MemoryRouter>
    );

    expect(screen.getByText(/did not complete/i)).toBeInTheDocument();
    expect(screen.queryByText(/secretReference/i)).not.toBeInTheDocument();
  });

  it('renders DashboardPage empty state when no accounts connected', async () => {
    mockKeycloak.authenticated = true;
    (accounts.dashboard as any).mockResolvedValueOnce({
      freshness: 'FRESH',
      connections: [],
    });
    (accounts.connections as any).mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /manage connections/i })).toBeInTheDocument();
      expect(screen.getByText(/total cash/i)).toBeInTheDocument();
    });
  });
});
