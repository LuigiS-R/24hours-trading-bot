create table app_user (
    user_id uuid primary key,
    external_subject varchar(255) not null unique, --Represents how a given user is seen in the ISP
    email varchar(320),
    display_name varchar(255),
    status varchar(20) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table alpaca_connection (
    connection_id uuid primary key,
    user_id uuid not null references app_user(user_id),
    environment varchar(16) not null,
    alpaca_account_id uuid not null,
    secret_reference varchar(512), -- Reference to the store where the OAuth token is stored
    granted_scopes varchar(255),
    status varchar(32) not null,
    last_verified_at timestamp with time zone,
    last_error_code varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (user_id, environment, alpaca_account_id),
    unique (environment, alpaca_account_id)
);

create table oauth_authorization_state ( --Used to track the state of a short-lived OAuth authorization request to prevent CSRF attacks
    state_id uuid primary key,
    state_hash varchar(64) not null unique,
    user_id uuid not null references app_user(user_id),
    environment varchar(16) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create table trading_account_projection (
    connection_id uuid primary key references alpaca_connection(connection_id), --Managed locally
    alpaca_account_id uuid not null, --Returned by Alpaca API as: 'id'
    account_number varchar(64),
    status varchar(32),
    currency varchar(8),
    cash numeric(24, 8),
    buying_power numeric(24, 8),
    non_marginable_buying_power numeric(24, 8),
    portfolio_value numeric(24, 8),
    equity numeric(24, 8),
    last_equity numeric(24, 8),
    long_market_value numeric(24, 8),
    account_blocked boolean not null default false,
    trading_blocked boolean not null default false,
    trade_suspended_by_user boolean not null default false,
    shorting_enabled boolean not null default false,
    last_synced_at timestamp with time zone --Managed locally
);

create table watchlist (
    watchlist_id uuid primary key,
    connection_id uuid not null references alpaca_connection(connection_id),
    alpaca_watchlist_id uuid not null unique,
    name varchar(255) not null,
    sync_status varchar(32) not null,
    last_sync_error_code varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (connection_id, name)
);

create table watchlist_entry (
    entry_id uuid primary key,
    watchlist_id uuid not null references watchlist(watchlist_id),
    ticker varchar(16) not null,
    is_active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (watchlist_id, ticker)
);

create table position_projection (
    position_id uuid primary key,
    connection_id uuid not null references alpaca_connection(connection_id),
    alpaca_asset_id uuid,
    symbol varchar(16) not null,
    quantity numeric(24, 8) not null,
    available_quantity numeric(24, 8),
    side varchar(8),
    average_entry_price numeric(24, 8),
    cost_basis numeric(24, 8),
    current_price numeric(24, 8),
    market_value numeric(24, 8),
    unrealized_pnl numeric(24, 8),
    updated_at timestamp with time zone not null,
    unique (connection_id, symbol)
);

create table processed_event (
    event_id uuid primary key,
    alpaca_event_id varchar(128),
    processed_at timestamp with time zone not null
);

create table outbox_event (
    outbox_id uuid primary key,
    event_type varchar(80) not null,
    payload text not null,
    status varchar(32) not null,
    attempts int not null default 0,
    created_at timestamp with time zone not null,
    published_at timestamp with time zone
);

create table watchlist_operation (
    operation_id uuid primary key,
    idempotency_key uuid not null unique,
    watchlist_id uuid,
    operation_type varchar(32) not null,
    request_payload text,
    status varchar(32) not null,
    result_identifier varchar(128),
    retry_count int not null default 0,
    error_code varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table audit_record (
    audit_id uuid primary key,
    actor varchar(255),
    action varchar(80) not null,
    resource_type varchar(80) not null,
    resource_id varchar(128),
    correlation_id uuid,
    summary varchar(1000),
    created_at timestamp with time zone not null
);
