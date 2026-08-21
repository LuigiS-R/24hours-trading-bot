package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.config.AlpacaWebSocketHandler;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

/** Maintains one account-scoped Alpaca trade-update stream per active connection. */
@Service
@Slf4j
public class TradeUpdateConnectionRegistry {
    static final int STARTUP_BATCH_SIZE = 10;
    private static final Duration INITIAL_RECONNECT_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofMinutes(1);

    private final AccountServiceClient accountServiceClient;
    private final WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper;
    private final PublisherService publisherService;
    private final AlpacaProperties alpacaProperties;
    private final ConcurrentMap<UUID, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ExecutorService bootstrapExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "alpaca-stream-bootstrap");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "alpaca-stream-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public TradeUpdateConnectionRegistry(
            AccountServiceClient accountServiceClient,
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            PublisherService publisherService,
            AlpacaProperties alpacaProperties) {
        this.accountServiceClient = accountServiceClient;
        this.webSocketClient = webSocketClient;
        this.objectMapper = objectMapper;
        this.publisherService = publisherService;
        this.alpacaProperties = alpacaProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openActiveConnectionsAtStartup() {
        bootstrapExecutor.execute(this::openInitialConnectionBatches);
    }

    void openInitialConnectionBatches() {
        List<AccountServiceClient.ActiveConnectionCredential> credentials;
        try {
            credentials = accountServiceClient.activeConnectionCredentials();
        } catch (RuntimeException exception) {
            log.error("Unable to retrieve active connections for trade-update streaming.");
            return;
        }

        for (int start = 0; start < credentials.size(); start += STARTUP_BATCH_SIZE) {
            int end = Math.min(start + STARTUP_BATCH_SIZE, credentials.size());
            List<CompletableFuture<Void>> attempts = new ArrayList<>();
            for (AccountServiceClient.ActiveConnectionCredential credential : credentials.subList(start, end)) {
                attempts.add(openConnection(credential));
            }
            CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new)).join();
        }
    }

    /** Opens a stream for a newly active connection after resolving only that connection's credential. */
    public void openConnection(UUID connectionId) {
        if (activeConnections.containsKey(connectionId)) {
            return;
        }
        try {
            AccountServiceClient.BrokerCredential credential = accountServiceClient.brokerCredential(connectionId);
            if (credential == null || credential.accessToken() == null || credential.accessToken().isBlank()) {
                log.warn("No usable broker credential is available for connection {}", connectionId);
                return;
            }
            openConnection(new AccountServiceClient.ActiveConnectionCredential(
                    credential.connectionId(), credential.accessToken()));
        } catch (RuntimeException exception) {
            log.error("Unable to open a trade-update stream for connection {}", connectionId);
        }
    }

    /**
     * Invalidates a connection generation before closing it. Any late completion
     * from a pending handshake sees that its registry identity is no longer current
     * and immediately closes its session rather than retaining or reconnecting it.
     */
    public void closeConnection(UUID connectionId) {
        ManagedConnection managedConnection = activeConnections.remove(connectionId);
        if (managedConnection == null) {
            return;
        }

        WebSocketSession session;
        synchronized (managedConnection) {
            managedConnection.desiredOpen = false;
            cancel(managedConnection.reconnectTask);
            managedConnection.reconnectTask = null;
            cancel(managedConnection.pendingConnection);
            managedConnection.pendingConnection = null;
            session = managedConnection.session;
            managedConnection.session = null;
        }
        closeQuietly(session);
        log.info("Closed Alpaca trade-update stream for connection {}", connectionId);
    }

    Set<UUID> activeConnectionIds() {
        return Set.copyOf(activeConnections.keySet());
    }

    private CompletableFuture<Void> openConnection(AccountServiceClient.ActiveConnectionCredential credential) {
        if (credential == null || credential.connectionId() == null
                || credential.accessToken() == null || credential.accessToken().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        UUID connectionId = credential.connectionId();
        ManagedConnection managedConnection = new ManagedConnection(credential.accessToken());
        if (activeConnections.putIfAbsent(connectionId, managedConnection) != null) {
            return CompletableFuture.completedFuture(null);
        }
        return startConnection(connectionId, managedConnection);
    }

    private CompletableFuture<Void> startConnection(UUID connectionId, ManagedConnection managedConnection) {
        long attempt;
        synchronized (managedConnection) {
            if (!isCurrentAndDesired(connectionId, managedConnection) || managedConnection.connecting) {
                return CompletableFuture.completedFuture(null);
            }
            managedConnection.connecting = true;
            managedConnection.streamClosed = false;
            attempt = ++managedConnection.attemptGeneration;
            cancel(managedConnection.reconnectTask);
            managedConnection.reconnectTask = null;
        }

        AlpacaWebSocketHandler handler = new AlpacaWebSocketHandler(
                connectionId,
                managedConnection.oauthToken,
                objectMapper,
                publisherService,
                alpacaProperties,
                () -> onSocketClosed(connectionId, managedConnection, attempt));
        final CompletableFuture<WebSocketSession> connectionFuture;
        try {
            connectionFuture = webSocketClient.execute(handler, URI.create(alpacaProperties.getStreamUrl()).toString());
        } catch (RuntimeException exception) {
            connectionAttemptFailed(connectionId, managedConnection, attempt);
            return CompletableFuture.completedFuture(null);
        }

        synchronized (managedConnection) {
            managedConnection.pendingConnection = connectionFuture;
        }
        return connectionFuture.handle((session, error) -> {
            if (error != null) {
                connectionAttemptFailed(connectionId, managedConnection, attempt);
                return null;
            }

            boolean retain;
            synchronized (managedConnection) {
                managedConnection.connecting = false;
                managedConnection.pendingConnection = null;
                retain = isCurrentAndDesired(connectionId, managedConnection)
                        && managedConnection.attemptGeneration == attempt
                        && !managedConnection.streamClosed;
                if (retain) {
                    managedConnection.session = session;
                    managedConnection.consecutiveFailures = 0;
                }
            }
            if (!retain) {
                closeQuietly(session);
            }
            return null;
        });
    }

    private void onSocketClosed(UUID connectionId, ManagedConnection managedConnection, long attempt) {
        synchronized (managedConnection) {
            if (!isCurrentAndDesired(connectionId, managedConnection)
                    || managedConnection.attemptGeneration != attempt) {
                return;
            }
            managedConnection.streamClosed = true;
            managedConnection.session = null;
            managedConnection.connecting = false;
            scheduleReconnect(connectionId, managedConnection);
        }
    }

    private void connectionAttemptFailed(UUID connectionId, ManagedConnection managedConnection, long attempt) {
        synchronized (managedConnection) {
            if (managedConnection.attemptGeneration != attempt) {
                return;
            }
            managedConnection.connecting = false;
            managedConnection.pendingConnection = null;
            if (isCurrentAndDesired(connectionId, managedConnection)) {
                scheduleReconnect(connectionId, managedConnection);
            }
        }
    }

    private void scheduleReconnect(UUID connectionId, ManagedConnection managedConnection) {
        if (managedConnection.reconnectTask != null && !managedConnection.reconnectTask.isDone()) {
            return;
        }
        Duration delay = reconnectDelay(managedConnection.consecutiveFailures++);
        log.info("Scheduling Alpaca trade-update reconnect for connection {} in {} seconds",
                connectionId, delay.toSeconds());
        managedConnection.reconnectTask = reconnectExecutor.schedule(
                () -> startConnection(connectionId, managedConnection),
                delay.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static Duration reconnectDelay(int consecutiveFailures) {
        long multiplier = 1L << Math.min(consecutiveFailures, 4);
        return Duration.ofSeconds(Math.min(
                INITIAL_RECONNECT_DELAY.toSeconds() * multiplier,
                MAX_RECONNECT_DELAY.toSeconds()));
    }

    private boolean isCurrentAndDesired(UUID connectionId, ManagedConnection managedConnection) {
        return managedConnection.desiredOpen && activeConnections.get(connectionId) == managedConnection;
    }

    @PreDestroy
    void closeAllConnections() {
        activeConnections.keySet().forEach(this::closeConnection);
        bootstrapExecutor.shutdownNow();
        reconnectExecutor.shutdownNow();
    }

    private static void cancel(CompletableFuture<WebSocketSession> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        if (session == null) {
            return;
        }
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException ignored) {
            // The connection is already being removed from the registry.
        }
    }

    private static final class ManagedConnection {
        private final String oauthToken;
        private boolean desiredOpen = true;
        private boolean connecting;
        private boolean streamClosed;
        private int consecutiveFailures;
        private long attemptGeneration;
        private CompletableFuture<WebSocketSession> pendingConnection;
        private WebSocketSession session;
        private ScheduledFuture<?> reconnectTask;

        private ManagedConnection(String oauthToken) {
            this.oauthToken = oauthToken;
        }
    }
}
