import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import {
  LoginPage,
  DashboardPage,
  ConnectionsPage,
  WatchlistDetailPage,
  ProfilePage,
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
    authorize: vi.fn(),
    reconcile: vi.fn(),
    watchlists: vi.fn(),
    watchlist: vi.fn(),
    createWatchlist: vi.fn(),
    disconnect: vi.fn(),
    addTicker: vi.fn(),
    toggleTicker: vi.fn(),
    removeTicker: vi.fn(),
    deleteWatchlist: vi.fn(),
    profile: vi.fn(),
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

it('renders DashboardPage with a connected paper account in FRESH state', async () => {
  mockKeycloak.authenticated = true;

  (accounts.dashboard as any).mockResolvedValueOnce({
    freshness: 'FRESH',
    totalCash: '100000.00',
    totalEquity: '101245.50',
    totalPortfolioValue: '101245.50',
    accounts: [
      {
        connection: {
          id: '11111111-1111-4111-8111-111111111111',
          status: 'CONNECTED',
          accountId: 'PA12345',
          environment: 'PAPER',
          lastSynchronizedAt: '2026-08-21T12:00:00Z',
        },
        portfolio: {
          cash: '100000.00',
          buyingPower: '200000.00',
          equity: '101245.50',
          portfolioValue: '101245.50',
          lastSynchronizedAt: '2026-08-21T12:00:00Z',
          freshness: 'FRESH',
        },
        positionCount: 2,
      },
    ],
  });

  (accounts.connections as any).mockResolvedValueOnce([
    {
      id: '11111111-1111-4111-8111-111111111111',
      status: 'CONNECTED',
      accountId: 'PA12345',
      environment: 'PAPER',
    },
  ]);

  render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(screen.getByText('PA12345')).toBeInTheDocument();
    expect(screen.getByText(/PAPER · CONNECTED/i)).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText(/Total cash/i)).toBeInTheDocument();
    expect(screen.getByText(/Portfolio value/i)).toBeInTheDocument();
  });
});

it('renders DashboardPage with a STALE freshness warning', async () => {
  mockKeycloak.authenticated = true;

  (accounts.dashboard as any).mockResolvedValueOnce({
    freshness: 'STALE',
    totalCash: '100000.00',
    totalEquity: '101245.50',
    totalPortfolioValue: '101245.50',
    accounts: [],
  });

  (accounts.connections as any).mockResolvedValueOnce([]);

  render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(screen.getByText(/STALE/i)).toBeInTheDocument();
  });
});

it('renders DashboardPage UNAVAILABLE state without reliable totals', async () => {
  mockKeycloak.authenticated = true;

  (accounts.dashboard as any).mockResolvedValueOnce({
    freshness: 'UNAVAILABLE',
    totalCash: '100000.00',
    totalEquity: '101245.50',
    totalPortfolioValue: '101245.50',
    accounts: [],
  });

  (accounts.connections as any).mockResolvedValueOnce([]);

  render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(
      screen.getByText(/Financial totals are unavailable/i)
    ).toBeInTheDocument();

    expect(screen.getByText(/Data unavailable/i)).toBeInTheDocument();
  });

  expect(screen.queryByText('$100,000.00')).not.toBeInTheDocument();
  expect(screen.queryByText('$101,245.50')).not.toBeInTheDocument();
});

it('starts Alpaca OAuth and redirects to the returned authorization URL', async () => {
  mockKeycloak.authenticated = true;

  (accounts.connections as any).mockResolvedValueOnce([]);
  (accounts.authorize as any).mockResolvedValueOnce({
    authorizationUrl: 'https://example.test/alpaca/oauth',
    expiresAt: '2026-08-23T12:00:00Z',
  });

  const assignSpy = vi
    .spyOn(window.location, 'assign')
    .mockImplementation(() => {});

  render(
    <MemoryRouter>
      <ConnectionsPage />
    </MemoryRouter>
  );

  const connectButton = await screen.findByRole('button', {
    name: /connect alpaca paper account/i,
  });

  fireEvent.click(connectButton);

  await waitFor(() => {
    expect(accounts.authorize).toHaveBeenCalledTimes(1);
    expect(assignSpy).toHaveBeenCalledWith(
      'https://example.test/alpaca/oauth'
    );
  });

  assignSpy.mockRestore();
});

it('reconciles a connected paper account successfully', async () => {
  mockKeycloak.authenticated = true;

  const connection = {
    id: '11111111-1111-4111-8111-111111111111',
    status: 'CONNECTED',
    accountId: 'PA12345',
    environment: 'PAPER',
  };

  (accounts.connections as any)
    .mockResolvedValueOnce([connection])
    .mockResolvedValueOnce([connection]);

  (accounts.watchlists as any).mockResolvedValue([]);

  (accounts.reconcile as any).mockResolvedValueOnce({
    status: 'CONNECTED',
  });

  render(
    <MemoryRouter>
      <ConnectionsPage />
    </MemoryRouter>
  );

  const reconcileButton = await screen.findByRole('button', {
    name: /reconcile now/i,
  });

  fireEvent.click(reconcileButton);

  await waitFor(() => {
    expect(accounts.reconcile).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111'
    );

    expect(
      screen.getByText(/Updated successfully/i)
    ).toBeInTheDocument();
  });
});

it('shows reconnect action when Alpaca connection requires reauthentication', async () => {
  mockKeycloak.authenticated = true;

  const connection = {
    id: '11111111-1111-4111-8111-111111111111',
    status: 'REAUTH_REQUIRED',
    accountId: 'PA12345',
    environment: 'PAPER',
  };

  (accounts.connections as any).mockResolvedValueOnce([connection]);
  (accounts.watchlists as any).mockResolvedValue([]);

  render(
    <MemoryRouter>
      <ConnectionsPage />
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(
      screen.getByText(/REAUTH_REQUIRED/i)
    ).toBeInTheDocument();
  });
});

it('creates a watchlist with normalized ticker symbols', async () => {
  mockKeycloak.authenticated = true;

  const connection = {
    id: '11111111-1111-4111-8111-111111111111',
    status: 'CONNECTED',
    accountId: 'PA12345',
    environment: 'PAPER',
  };

  (accounts.connections as any).mockResolvedValueOnce([connection]);
  (accounts.watchlists as any).mockResolvedValueOnce([]);
  (accounts.createWatchlist as any).mockResolvedValueOnce({
    id: '22222222-2222-4222-8222-222222222222',
    name: 'Tech',
  });

  const assignSpy = vi
    .spyOn(window.location, 'assign')
    .mockImplementation(() => {});

  render(
    <MemoryRouter>
      <ConnectionsPage />
    </MemoryRouter>
  );

  const createButton = await screen.findByRole('button', {
    name: /create watchlist/i,
  });

  fireEvent.click(createButton);

  fireEvent.change(screen.getByLabelText(/name/i), {
    target: { value: 'Tech' },
  });

  fireEvent.change(screen.getByLabelText(/initial symbols/i), {
    target: { value: ' aapl, msft ' },
  });

  fireEvent.click(
    screen.getByRole('button', {
      name: /save watchlist/i,
    })
  );

  await waitFor(() => {
    expect(accounts.createWatchlist).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
      'Tech',
      ['AAPL', 'MSFT']
    );

    expect(assignSpy).toHaveBeenCalledWith(
      '/watchlists/22222222-2222-4222-8222-222222222222'
    );
  });

  assignSpy.mockRestore();
});

it('shows a safe error when watchlist creation fails', async () => {
  mockKeycloak.authenticated = true;

  const connection = {
    id: '11111111-1111-4111-8111-111111111111',
    status: 'CONNECTED',
    accountId: 'PA12345',
    environment: 'PAPER',
  };

  (accounts.connections as any).mockResolvedValueOnce([connection]);
  (accounts.watchlists as any).mockResolvedValueOnce([]);

  (accounts.createWatchlist as any).mockRejectedValueOnce(
    new Error('Alpaca is temporarily unavailable.')
  );

  render(
    <MemoryRouter>
      <ConnectionsPage />
    </MemoryRouter>
  );

  const createButton = await screen.findByRole('button', {
    name: /create watchlist/i,
  });

  fireEvent.click(createButton);

  fireEvent.change(screen.getByLabelText(/name/i), {
    target: { value: 'Tech' },
  });

  fireEvent.change(screen.getByLabelText(/initial symbols/i), {
    target: { value: 'AAPL' },
  });

  fireEvent.click(
    screen.getByRole('button', {
      name: /save watchlist/i,
    })
  );

  await waitFor(() => {
    expect(
      screen.getByText(/Alpaca is temporarily unavailable/i)
    ).toBeInTheDocument();
  });
});

it('renders watchlists for the selected connected paper account', async () => {
  mockKeycloak.authenticated = true;

  const connection = {
    id: '11111111-1111-4111-8111-111111111111',
    status: 'CONNECTED',
    accountId: 'PA39102941',
    environment: 'PAPER',
  };

  (accounts.connections as any).mockResolvedValueOnce([connection]);

  (accounts.watchlists as any).mockResolvedValueOnce([
    {
      id: '22222222-2222-4222-8222-222222222222',
      name: 'Tech Momentum',
      entryCount: 3,
      activeEntryCount: 2,
      status: 'SYNCED',
    },
    {
      id: '33333333-3333-4333-8333-333333333333',
      name: 'Crypto Paper',
      entryCount: 2,
      activeEntryCount: 1,
      status: 'SYNCED',
    },
  ]);

  render(
    <MemoryRouter>
      <ConnectionsPage />
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(screen.getByText('PA39102941')).toBeInTheDocument();

    expect(screen.getByText('Tech Momentum')).toBeInTheDocument();
    expect(screen.getByText('Crypto Paper')).toBeInTheDocument();

    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getAllByText('2')).toHaveLength(2);
    expect(screen.getByText('1')).toBeInTheDocument();
  });
});

it('confirms before disconnecting a paper account', async () => {
  // ...your test #18...
});


// TEST #19 — ADD IT HERE
it('renders an existing watchlist with its ticker entries', async () => {
  mockKeycloak.authenticated = true;

  (accounts.watchlist as any).mockResolvedValueOnce({
    id: '22222222-2222-4222-8222-222222222222',
    name: 'Tech Momentum',
    status: 'SYNCED',
    entries: [
      {
        ticker: 'AAPL',
        symbol: 'AAPL',
        active: true,
      },
      {
        ticker: 'MSFT',
        symbol: 'MSFT',
        active: false,
      },
      {
        ticker: 'NVDA',
        symbol: 'NVDA',
        active: true,
      },
    ],
  });

  render(
    <MemoryRouter
      initialEntries={[
        '/watchlists/22222222-2222-4222-8222-222222222222',
      ]}
    >
      <Routes>
        <Route
          path="/watchlists/:watchlistId"
          element={<WatchlistDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(screen.getByText('Tech Momentum')).toBeInTheDocument();
    expect(screen.getByText('AAPL')).toBeInTheDocument();
    expect(screen.getByText('MSFT')).toBeInTheDocument();
    expect(screen.getByText('NVDA')).toBeInTheDocument();
  });
});

it('adds a normalized ticker to an existing watchlist', async () => {
  mockKeycloak.authenticated = true;

  const watchlistId = '22222222-2222-4222-8222-222222222222';

  (accounts.watchlist as any)
    .mockResolvedValueOnce({
      id: watchlistId,
      name: 'Tech Momentum',
      status: 'SYNCED',
      entries: [
        {
          ticker: 'AAPL',
          symbol: 'AAPL',
          active: true,
        },
      ],
    })
    .mockResolvedValueOnce({
      id: watchlistId,
      name: 'Tech Momentum',
      status: 'SYNCED',
      entries: [
        {
          ticker: 'AAPL',
          symbol: 'AAPL',
          active: true,
        },
        {
          ticker: 'NVDA',
          symbol: 'NVDA',
          active: true,
        },
      ],
    });

  (accounts.addTicker as any).mockResolvedValueOnce(null);

  render(
    <MemoryRouter initialEntries={[`/watchlists/${watchlistId}`]}>
      <Routes>
        <Route
          path="/watchlists/:watchlistId"
          element={<WatchlistDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  );

  const tickerInput = await screen.findByPlaceholderText(/ticker/i);

  fireEvent.change(tickerInput, {
    target: { value: ' nvda ' },
  });

  fireEvent.click(
    screen.getByRole('button', {
      name: /add ticker/i,
    })
  );

  await waitFor(() => {
    expect(accounts.addTicker).toHaveBeenCalledWith(
      watchlistId,
      'NVDA'
    );
  });
});

it('toggles monitoring for a watchlist ticker', async () => {
  mockKeycloak.authenticated = true;

  const watchlistId = '22222222-2222-4222-8222-222222222222';

  (accounts.watchlist as any)
    .mockResolvedValueOnce({
      id: watchlistId,
      name: 'Tech Momentum',
      status: 'SYNCED',
      entries: [
        {
          ticker: 'AAPL',
          symbol: 'AAPL',
          active: true,
        },
      ],
    })
    .mockResolvedValueOnce({
      id: watchlistId,
      name: 'Tech Momentum',
      status: 'SYNCED',
      entries: [
        {
          ticker: 'AAPL',
          symbol: 'AAPL',
          active: false,
        },
      ],
    });

  (accounts.toggleTicker as any).mockResolvedValueOnce(null);

  render(
    <MemoryRouter initialEntries={[`/watchlists/${watchlistId}`]}>
      <Routes>
        <Route
          path="/watchlists/:watchlistId"
          element={<WatchlistDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  );

  const toggleCheckbox = await screen.findByRole('checkbox');

  fireEvent.click(toggleCheckbox);

  await waitFor(() => {
    expect(accounts.toggleTicker).toHaveBeenCalledWith(
      watchlistId,
      'AAPL',
      false
    );
  });
});

it('confirms before removing a ticker from a watchlist', async () => {
  mockKeycloak.authenticated = true;

  const watchlistId = '22222222-2222-4222-8222-222222222222';

  (accounts.watchlist as any)
    .mockResolvedValueOnce({
      id: watchlistId,
      name: 'Tech Momentum',
      status: 'SYNCED',
      entries: [
        {
          ticker: 'AAPL',
          symbol: 'AAPL',
          active: true,
        },
      ],
    })
    .mockResolvedValueOnce({
      id: watchlistId,
      name: 'Tech Momentum',
      status: 'SYNCED',
      entries: [],
    });

  (accounts.removeTicker as any).mockResolvedValueOnce(null);

  render(
    <MemoryRouter initialEntries={[`/watchlists/${watchlistId}`]}>
      <Routes>
        <Route
          path="/watchlists/:watchlistId"
          element={<WatchlistDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  );

  const removeButton = await screen.findByRole('button', {
    name: /^remove$/i,
  });

  fireEvent.click(removeButton);

  expect(
    screen.getByText(/Remove AAPL\?/i)
  ).toBeInTheDocument();

  expect(
    screen.getByText(/This changes the Alpaca broker watchlist/i)
  ).toBeInTheDocument();

  fireEvent.click(
    screen.getByRole('button', {
      name: /confirm/i,
    })
  );

  await waitFor(() => {
    expect(accounts.removeTicker).toHaveBeenCalledWith(
      watchlistId,
      'AAPL'
    );
  });
});

it('confirms before deleting a watchlist', async () => {
  mockKeycloak.authenticated = true;

  const watchlistId = '22222222-2222-4222-8222-222222222222';

  (accounts.watchlist as any).mockResolvedValueOnce({
    id: watchlistId,
    name: 'Tech Momentum',
    status: 'SYNCED',
    entries: [
      {
        ticker: 'AAPL',
        symbol: 'AAPL',
        active: true,
      },
    ],
  });

  (accounts.deleteWatchlist as any).mockResolvedValueOnce(null);

  render(
    <MemoryRouter initialEntries={[`/watchlists/${watchlistId}`]}>
      <Routes>
        <Route
          path="/watchlists/:watchlistId"
          element={<WatchlistDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  );

  const deleteButton = await screen.findByRole('button', {
    name: /delete watchlist/i,
  });

  fireEvent.click(deleteButton);

  expect(
    screen.getByRole('dialog')
  ).toBeInTheDocument();

  expect(
    screen.getByText(/Delete watchlist\?/i)
  ).toBeInTheDocument();

  expect(
    screen.getByText(/This changes the Alpaca broker watchlist/i)
  ).toBeInTheDocument();

  fireEvent.click(
    screen.getByRole('button', {
      name: /confirm/i,
    })
  );

  await waitFor(() => {
    expect(accounts.deleteWatchlist).toHaveBeenCalledWith(
      watchlistId
    );
  });
});

it('renders the authenticated user profile', async () => {
  mockKeycloak.authenticated = true;

  (accounts.profile as any).mockResolvedValueOnce({
    id: 'user-123',
    email: 'trader@example.com',
    displayName: 'TraderBotUser',
  });

  render(
    <MemoryRouter>
      <ProfilePage />
    </MemoryRouter>
  );

  await waitFor(() => {
    expect(
      screen.getByLabelText(/Display name/i)
    ).toHaveValue('TraderBotUser');

    expect(
      screen.getByLabelText(/Email/i)
    ).toHaveValue('trader@example.com');
  });
});