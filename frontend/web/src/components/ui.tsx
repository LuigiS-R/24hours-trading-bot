import { Link, NavLink } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import type { Connection, Freshness } from "../api/types";
export const Money = ({ value }: { value?: string }) => (
  <span>
    {value === undefined || value === null
      ? "—"
      : new Intl.NumberFormat(undefined, {
          style: "currency",
          currency: "USD",
          minimumFractionDigits: 2,
        }).format(Number(value))}
  </span>
);
export const DateTime = ({ value }: { value?: string }) => (
  <span>
    {value
      ? new Intl.DateTimeFormat(undefined, {
          dateStyle: "medium",
          timeStyle: "short",
        }).format(new Date(value))
      : "Not available"}
  </span>
);
export function Badge({ status }: { status?: string }) {
  return (
    <span
      className={`badge ${status?.toLowerCase().replaceAll("_", "-") || ""}`}
    >
      {status?.replaceAll("_", " ") || "Unknown"}
    </span>
  );
}
export function FreshnessBadge({ value }: { value?: Freshness }) {
  return (
    <span className={`badge ${value?.toLowerCase() || "unavailable"}`}>
      {value === "STALE"
        ? "Stale · data may be out of date"
        : value === "FRESH"
          ? "Fresh data"
          : "Data unavailable"}
    </span>
  );
}
export function Loading() {
  return (
    <div className="panel loading" role="status">
      Loading account data…
    </div>
  );
}
export function ErrorPanel({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <section className="panel error" role="alert">
      <p>{message}</p>
      {retry && <button onClick={retry}>Try again</button>}
    </section>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: React.ReactNode;
}) {
  return (
    <section className="panel empty">
      <h2>{title}</h2>
      <p>{children}</p>
    </section>
  );
}
export function AppShell({
  children,
  connections = [],
  selected,
  onSelect,
}: {
  children: React.ReactNode;
  connections?: Connection[];
  selected?: string;
  onSelect?: (id: string) => void;
}) {
  const { keycloak } = useKeycloak();
  const name = keycloak.tokenParsed?.preferred_username || "Trader";
  return (
    <div className="shell">
      <aside>
        <Link className="brand" to="/dashboard">
          Tradify <small>paper</small>
        </Link>
        <nav aria-label="Main navigation">
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/connections">Connections</NavLink>
          {selected && (
            <NavLink to="/connections#watchlists">Watchlists</NavLink>
          )}
          <NavLink to="/profile">Profile</NavLink>
        </nav>
        <button
          className="quiet logout"
          onClick={() =>
            keycloak.logout({ redirectUri: `${window.location.origin}/login` })
          }
        >
          Log out
        </button>
      </aside>
      <header>
        <span className="paper">PAPER TRADING</span>
        {connections.length > 0 && (
          <label className="selector">
            Paper account
            <select
              value={selected || ""}
              onChange={(e) => onSelect?.(e.target.value)}
            >
              <option value="">Select account</option>
              {connections.map((c) => (
                <option
                  value={c.id || c.connectionId}
                  key={c.id || c.connectionId}
                >
                  {c.accountId || c.alpacaAccountId || "Paper account"} ·{" "}
                  {c.status}
                </option>
              ))}
            </select>
          </label>
        )}
        <span className="user">{String(name)}</span>
      </header>
      <main>{children}</main>
    </div>
  );
}
export function Confirm({
  title,
  children,
  onCancel,
  onConfirm,
  pending,
}: {
  title: string;
  children: React.ReactNode;
  onCancel: () => void;
  onConfirm: () => void;
  pending?: boolean;
}) {
  return (
    <div className="modal-backdrop" role="presentation">
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
      >
        <h2 id="confirm-title">{title}</h2>
        <p>{children}</p>
        <div className="actions">
          <button className="quiet" disabled={pending} onClick={onCancel}>
            Cancel
          </button>
          <button className="danger" disabled={pending} onClick={onConfirm}>
            {pending ? "Working…" : "Confirm"}
          </button>
        </div>
      </section>
    </div>
  );
}
