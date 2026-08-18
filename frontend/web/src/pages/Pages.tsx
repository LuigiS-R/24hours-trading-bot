import { useEffect, useState } from "react";
import {
  Link,
  Navigate,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { accounts, errorText } from "../api/accountsClient";
import type {
  Connection,
  Dashboard,
  Order,
  Portfolio,
  Position,
  Watchlist,
} from "../api/types";
import {
  AppShell,
  Badge,
  Confirm,
  DateTime,
  Empty,
  ErrorPanel,
  FreshnessBadge,
  Loading,
  Money,
} from "../components/ui";
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function useConnections() {
  const [connections, setConnections] = useState<Connection[]>([]),
    [error, setError] = useState("");
  const load = () =>
    accounts
      .connections()
      .then(setConnections)
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    load();
  }, []);
  return { connections, error, load };
}
export function LoginPage() {
  const { initialized, keycloak } = useKeycloak();
  if (initialized && keycloak.authenticated)
    return <Navigate to="/dashboard" replace />;
  return (
    <main className="login">
      <section className="panel">
        <span className="paper">PAPER TRADING</span>
        <h1>Tradify</h1>
        <p className="muted">
          A calm control room for your algorithmic paper-trading account.
        </p>
        <p>Paper trading only. No real-money orders are placed.</p>
        {!initialized ? (
          <Loading />
        ) : (
          <div className="actions">
            <button onClick={() => keycloak.login()}>Log in</button>
            <button className="quiet" onClick={() => keycloak.register()}>
              Create account
            </button>
          </div>
        )}
      </section>
    </main>
  );
}
export function DashboardPage() {
  const { connections, error: connectionError, load } = useConnections();
  const [data, setData] = useState<Dashboard>(),
    [error, setError] = useState(""),
    [selected, setSelected] = useState("");
  const reload = () =>
    accounts
      .dashboard()
      .then(setData)
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    reload();
  }, []);
  const c = connections.find((x) => (x.id || x.connectionId) === selected);
  return (
    <AppShell
      connections={connections}
      selected={selected}
      onSelect={setSelected}
    >
      {!data && !error ? (
        <Loading />
      ) : (
        <>
          <div className="page-head">
            <div>
              <h1>Dashboard</h1>
              <p className="muted">
                Your paper account projections, supplied by Accounts Service.
              </p>
            </div>
            <div className="actions">
              <button onClick={reload}>Refresh dashboard</button>
              <Link to="/connections">
                <button className="quiet">Manage connections</button>
              </Link>
            </div>
          </div>
          {(error || connectionError) && (
            <ErrorPanel
              message={error || connectionError}
              retry={() => {
                reload();
                load();
              }}
            />
          )}
          {data?.freshness === "UNAVAILABLE" && (
            <section className="notice">
              Financial totals are unavailable. Reconcile a paper account to
              refresh projections.
            </section>
          )}
          {data?.freshness && <FreshnessBadge value={data.freshness} />}{" "}
          {data?.freshness === "FRESH" && (
            <section className="grid" style={{ marginTop: 16 }}>
              <Metric
                label="Total cash"
                value={<Money value={data.totalCash} />}
              />
              <Metric
                label="Total equity"
                value={<Money value={data.totalEquity} />}
              />
              <Metric
                label="Portfolio value"
                value={<Money value={data.totalPortfolioValue} />}
              />
            </section>
          )}
          {data?.accounts?.length === 0 && (
            <Empty title="Connect your Alpaca paper account">
              <Link to="/connections">
                <button>Connect Alpaca Paper Account</button>
              </Link>
            </Empty>
          )}
          {data?.accounts?.map(({ connection, portfolio, positionCount }) => (
            <section
              className="panel account-card"
              key={connection.id || connection.connectionId}
            >
              <div className="card-top">
                <div>
                  <h2>
                    {connection.accountId ||
                      connection.alpacaAccountId ||
                      "Alpaca paper account"}
                  </h2>
                  <p className="muted">PAPER · {connection.status}</p>
                </div>
                <Badge status={connection.status} />
              </div>
              <div className="grid">
                <Metric
                  label="Cash"
                  value={<Money value={portfolio?.cash} />}
                />
                <Metric
                  label="Buying power"
                  value={<Money value={portfolio?.buyingPower} />}
                />
                <Metric
                  label="Equity"
                  value={<Money value={portfolio?.equity} />}
                />
                <Metric label="Positions" value={positionCount ?? "—"} />
              </div>
              <p className="muted">
                Last synchronization:{" "}
                <DateTime
                  value={
                    portfolio?.lastSynchronizedAt ||
                    connection.lastSynchronizedAt
                  }
                />
              </p>
              <div className="actions">
                <Link
                  to={`/connections?account=${connection.id || connection.connectionId}`}
                >
                  <button className="quiet">View detailed portfolio</button>
                </Link>
                <Link to="/connections">
                  <button className="quiet">Manage watchlists</button>
                </Link>
              </div>
            </section>
          ))}
        </>
      )}
    </AppShell>
  );
}
function Metric({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="panel metric">
      <label>{label}</label>
      <strong>{value}</strong>
    </div>
  );
}
export function ConnectionsPage() {
  const { connections, error, load } = useConnections();
  const [busy, setBusy] = useState(""),
    [confirm, setConfirm] = useState<Connection>();
  const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
  const [create, setCreate] = useState(false),
    [name, setName] = useState(""),
    [symbols, setSymbols] = useState(""),
    [message, setMessage] = useState("");
  const [search] = useSearchParams();
  const account =
    search.get("account") ||
    connections.find((c) => c.status === "CONNECTED")?.id ||
    connections[0]?.id;
  const selected = connections.find(
    (c) => c.id === account || c.connectionId === account,
  );
  const loadLists = () =>
    account &&
    accounts
      .watchlists(account)
      .then(setWatchlists)
      .catch((e) => setMessage(errorText(e)));
  useEffect(() => {
    if (account) loadLists();
  }, [account]);
  async function run(id: string, fn: () => Promise<unknown>) {
    setBusy(id);
    setMessage("");
    try {
      await fn();
      await load();
      await loadLists();
      setMessage("Updated successfully.");
    } catch (e) {
      setMessage(errorText(e));
    } finally {
      setBusy("");
    }
  }
  async function connect() {
    setBusy("connect");
    try {
      const { authorizationUrl } = await accounts.authorize();
      window.location.assign(authorizationUrl);
    } catch (e) {
      setMessage(errorText(e));
      setBusy("");
    }
  }
  return (
    <AppShell
      connections={connections}
      selected={account}
      onSelect={(id) => window.location.assign(`/connections?account=${id}`)}
    >
      <div className="page-head">
        <div>
          <h1>Paper account connections</h1>
          <p className="muted">Only Alpaca paper accounts can be connected.</p>
        </div>
        <button disabled={busy === "connect"} onClick={connect}>
          {busy === "connect"
            ? "Opening Alpaca…"
            : "Connect Alpaca Paper Account"}
        </button>
      </div>
      {(error || message) && (
        <section className={error ? "panel error" : "notice"} role="status">
          {error || message}
        </section>
      )}
      {connections.map((c) => {
        const id = c.id || c.connectionId || "";
        return (
          <section className="panel account-card" key={id}>
            <div className="card-top">
              <div>
                <h2>{c.accountId || c.alpacaAccountId || "Paper account"}</h2>
                <p className="muted">
                  PAPER · verified <DateTime value={c.lastVerifiedAt} />
                </p>
              </div>
              <Badge status={c.status} />
            </div>
            {c.errorCode && (
              <p className="notice">Safe error code: {c.errorCode}</p>
            )}
            <div className="actions">
              {["CONNECTED", "PENDING", "ERROR"].includes(c.status) && (
                <button
                  disabled={busy === id}
                  onClick={() => run(id, () => accounts.reconcile(id))}
                >
                  {busy === id ? "Reconciling…" : "Reconcile now"}
                </button>
              )}
              <button
                className="danger"
                disabled={busy === id}
                onClick={() => setConfirm(c)}
              >
                Disconnect
              </button>
            </div>
          </section>
        );
      })}
      {selected && (
        <section className="panel" id="watchlists">
          <div className="page-head">
            <div>
              <h2>Watchlists</h2>
              <p className="muted">
                For{" "}
                {selected.accountId ||
                  selected.alpacaAccountId ||
                  "selected paper account"}
              </p>
            </div>
            <button onClick={() => setCreate(true)}>Create watchlist</button>
          </div>
          {watchlists.length === 0 ? (
            <p className="muted">No watchlists yet.</p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Entries</th>
                    <th>Active</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {watchlists.map((w) => (
                    <tr key={w.id}>
                      <td>{w.name}</td>
                      <td>{w.entryCount ?? w.entries?.length ?? 0}</td>
                      <td>
                        {w.activeEntryCount ??
                          w.entries?.filter((e) => e.active).length ??
                          0}
                      </td>
                      <td>
                        <Badge status={w.status} />
                      </td>
                      <td>
                        <Link to={`/watchlists/${w.id}`}>Manage</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
      {confirm && (
        <Confirm
          title="Disconnect paper account?"
          onCancel={() => setConfirm(undefined)}
          onConfirm={() => {
            const id = confirm.id || confirm.connectionId || "";
            setConfirm(undefined);
            run(id, () => accounts.disconnect(id));
          }}
        >
          Disconnect{" "}
          {confirm.accountId || confirm.alpacaAccountId || "this paper account"}
          ? Its local projections will no longer be available.
        </Confirm>
      )}
      {create && (
        <WatchlistForm
          title="Create watchlist"
          pending={busy === "create"}
          name={name}
          symbols={symbols}
          setName={setName}
          setSymbols={setSymbols}
          onCancel={() => setCreate(false)}
          onSave={async () => {
            const tickers = validateSymbols(symbols);
            if (!name.trim() || tickers === null) {
              setMessage("Enter a name and unique valid symbols.");
              return;
            }
            setBusy("create");
            try {
              const w = await accounts.createWatchlist(
                account!,
                name.trim(),
                tickers,
              );
              window.location.assign(`/watchlists/${w.id}`);
            } catch (e) {
              setMessage(errorText(e));
              setBusy("");
            }
          }}
        />
      )}
    </AppShell>
  );
}
function validateSymbols(raw: string) {
  const list = raw
    .split(",")
    .map((x) => x.trim().toUpperCase())
    .filter(Boolean);
  return list.every((x) => /^[A-Z][A-Z.]{0,15}$/.test(x)) &&
    new Set(list).size === list.length
    ? list
    : null;
}
function WatchlistForm(p: {
  title: string;
  pending: boolean;
  name: string;
  symbols: string;
  setName: (x: string) => void;
  setSymbols: (x: string) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  return (
    <div className="modal-backdrop">
      <section className="modal" role="dialog" aria-modal="true">
        <h2>{p.title}</h2>
        <div className="form">
          <label>
            Name
            <input
              autoFocus
              value={p.name}
              onChange={(e) => p.setName(e.target.value)}
              disabled={p.pending}
            />
          </label>
          <label>
            Initial symbols{" "}
            <span className="muted">Optional, comma separated</span>
            <input
              placeholder="AAPL, MSFT"
              value={p.symbols}
              onChange={(e) => p.setSymbols(e.target.value)}
              disabled={p.pending}
            />
          </label>
          <div className="actions">
            <button className="quiet" disabled={p.pending} onClick={p.onCancel}>
              Cancel
            </button>
            <button disabled={p.pending} onClick={p.onSave}>
              {p.pending ? "Saving…" : "Save watchlist"}
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
export function WatchlistDetailPage() {
  const { watchlistId = "" } = useParams();
  const nav = useNavigate();
  const [data, setData] = useState<Watchlist>(),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(""),
    [ticker, setTicker] = useState(""),
    [remove, setRemove] = useState<string>();
  const load = () => {
    if (!uuid.test(watchlistId)) {
      setError("This watchlist link is invalid.");
      return;
    }
    accounts
      .watchlist(watchlistId)
      .then(setData)
      .catch((e) => setError(errorText(e)));
  };
  useEffect(() => {
    load();
  }, [watchlistId]);
  const mutate = async (action: string, fn: () => Promise<unknown>) => {
    setBusy(action);
    setError("");
    try {
      await fn();
      await load();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy("");
    }
  };
  if (error)
    return (
      <AppShell>
        <ErrorPanel message={error} retry={load} />
      </AppShell>
    );
  if (!data)
    return (
      <AppShell>
        <Loading />
      </AppShell>
    );
  return (
    <AppShell>
      <div className="page-head">
        <div>
          <h1>{data.name}</h1>
          <p className="muted">
            Watchlist status: <Badge status={data.status} />
          </p>
        </div>
        <button
          className="danger"
          disabled={!!busy}
          onClick={() => setRemove("__watchlist__")}
        >
          Delete watchlist
        </button>
      </div>
      <section className="notice">
        Turning off a ticker stops bot monitoring signals; it does not sell
        holdings or cancel orders.
      </section>
      <section className="panel">
        <h2>Entries</h2>
        <form
          className="actions"
          onSubmit={(e) => {
            e.preventDefault();
            const parsed = validateSymbols(ticker);
            if (!parsed || parsed.length !== 1) {
              setError("Enter one valid ticker, such as AAPL.");
              return;
            }
            mutate("add", () => accounts.addTicker(data.id, parsed[0]));
            setTicker("");
          }}
        >
          <label className="visually-hidden" htmlFor="ticker">
            Ticker
          </label>
          <input
            id="ticker"
            value={ticker}
            onChange={(e) => setTicker(e.target.value)}
            placeholder="Add ticker"
          />
          <button disabled={busy === "add"}>
            {busy === "add" ? "Adding…" : "Add ticker"}
          </button>
        </form>
        {data.entries?.length === 0 ? (
          <p className="muted">No ticker entries.</p>
        ) : (
          data.entries?.map((entry) => {
            const symbol = entry.ticker || entry.symbol || "";
            return (
              <div className="ticker-row" key={symbol}>
                <strong>{symbol}</strong>
                <div className="actions">
                  <label className="toggle">
                    <input
                      type="checkbox"
                      checked={entry.active}
                      onChange={() =>
                        mutate(`toggle-${symbol}`, () =>
                          accounts.toggleTicker(data.id, symbol, !entry.active),
                        )
                      }
                      disabled={!!busy}
                    />
                    {entry.active ? "Monitoring on" : "Monitoring off"}
                  </label>
                  <button
                    className="danger"
                    disabled={!!busy}
                    onClick={() => setRemove(symbol)}
                  >
                    Remove
                  </button>
                </div>
              </div>
            );
          })
        )}
      </section>
      {remove && (
        <Confirm
          title={
            remove === "__watchlist__"
              ? "Delete watchlist?"
              : `Remove ${remove}?`
          }
          pending={!!busy}
          onCancel={() => setRemove(undefined)}
          onConfirm={() => {
            if (remove === "__watchlist__")
              mutate("delete", () => accounts.deleteWatchlist(data.id)).then(
                () => nav("/connections"),
              );
            else mutate("remove", () => accounts.removeTicker(data.id, remove));
            setRemove(undefined);
          }}
        >
          {remove === "__watchlist__"
            ? "This changes the Alpaca broker watchlist."
            : "This changes the Alpaca broker watchlist."}
        </Confirm>
      )}
    </AppShell>
  );
}
export function ProfilePage() {
  const [profile, setProfile] = useState<{
      email: string;
      displayName: string;
    }>(),
    [name, setName] = useState(""),
    [message, setMessage] = useState(""),
    [pending, setPending] = useState(false);
  useEffect(() => {
    accounts
      .profile()
      .then((p) => {
        setProfile(p);
        setName(p.displayName);
      })
      .catch((e) => setMessage(errorText(e)));
  }, []);
  if (!profile)
    return (
      <AppShell>
        <Loading />
      </AppShell>
    );
  return (
    <AppShell>
      <div className="page-head">
        <div>
          <h1>Profile</h1>
          <p className="muted">Your personal account preferences.</p>
        </div>
      </div>
      {message && (
        <section className="notice" role="status">
          {message}
        </section>
      )}
      <form
        className="panel form"
        onSubmit={async (e) => {
          e.preventDefault();
          setPending(true);
          setMessage("");
          try {
            const p = await accounts.updateProfile(name.trim());
            setProfile(p);
            setName(p.displayName);
            setMessage("Display name updated.");
          } catch (e) {
            setMessage(errorText(e));
          } finally {
            setPending(false);
          }
        }}
      >
        <label>
          Email
          <input value={profile.email} readOnly aria-readonly="true" />
        </label>
        <label>
          Display name
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            disabled={pending}
          />
        </label>
        <div>
          <button disabled={pending}>
            {pending ? "Saving…" : "Save changes"}
          </button>
        </div>
      </form>
    </AppShell>
  );
}
export function OAuthSuccessPage() {
  const [search] = useSearchParams();
  const id = search.get("connectionId") || "";
  const [connection, setConnection] = useState<Connection>(),
    [error, setError] = useState("");
  useEffect(() => {
    if (!uuid.test(id)) {
      setError("The connection reference is invalid.");
      return;
    }
    accounts
      .connection(id)
      .then(setConnection)
      .catch((e) => setError(errorText(e)));
  }, [id]);
  return (
    <AppShell>
      {error ? (
        <ErrorPanel message={error} />
      ) : !connection ? (
        <Loading />
      ) : (
        <section className="panel">
          <span className="badge connected">CONNECTED</span>
          <h1>Paper account connected</h1>
          <p>
            Your Alpaca PAPER account{" "}
            {connection.accountId || connection.alpacaAccountId || "is ready"}{" "}
            is connected.
          </p>
          <div className="actions">
            <Link to="/dashboard">
              <button>View dashboard</button>
            </Link>
            <Link to="/connections">
              <button className="quiet">Manage watchlists</button>
            </Link>
          </div>
        </section>
      )}
    </AppShell>
  );
}
export function OAuthFailurePage() {
  const { keycloak } = useKeycloak();
  return (
    <main className="login">
      <section className="panel">
        <h1>Connection did not complete</h1>
        <p className="muted">
          Your Alpaca paper account was not connected. No credentials or
          authorization details are shown here.
        </p>
        <div className="actions">
          <Link to={keycloak.authenticated ? "/connections" : "/login"}>
            <button>Try again</button>
          </Link>
          {keycloak.authenticated && (
            <Link to="/dashboard">
              <button className="quiet">Return to dashboard</button>
            </Link>
          )}
        </div>
      </section>
    </main>
  );
}
export function NotFoundPage() {
  const { keycloak } = useKeycloak();
  return (
    <main className="login">
      <section className="panel">
        <h1>Page not found</h1>
        <p className="muted">
          That page does not exist or is no longer available.
        </p>
        <Link to={keycloak.authenticated ? "/dashboard" : "/login"}>
          <button>Return</button>
        </Link>
      </section>
    </main>
  );
}
