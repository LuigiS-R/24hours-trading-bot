package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.config.AlpacaWebSocketHandler;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** Closes and removes the stream, including any in-progress connection attempt. */
    public void closeConnection(UUID connectionId) {
        ManagedConnection managedConnection = activeConnections.remove(connectionId);
        if (managedConnection != null) {
            managedConnection.close();
            log.info("Closed Alpaca trade-update stream for connection {}", connectionId);
        }
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
        ManagedConnection managedConnection = new ManagedConnection();
        if (activeConnections.putIfAbsent(connectionId, managedConnection) != null) {
            return CompletableFuture.completedFuture(null);
        }

        AlpacaWebSocketHandler handler = new AlpacaWebSocketHandler(
                connectionId,
                credential.accessToken(),
                objectMapper,
                publisherService,
                alpacaProperties,
                () -> activeConnections.remove(connectionId, managedConnection));
        try {
            CompletableFuture<WebSocketSession> future = webSocketClient.execute(
                    handler, URI.create(alpacaProperties.getStreamUrl()).toString());
            managedConnection.setFuture(future);
            return future.handle((session, error) -> {
                if (error != null) {
                    activeConnections.remove(connectionId, managedConnection);
                    log.warn("Unable to establish Alpaca trade-update stream for connection {}", connectionId);
                } else {
                    managedConnection.setSession(session);
                    if (activeConnections.get(connectionId) != managedConnection) {
                        closeQuietly(session);
                    }
                }
                return null;
            });
        } catch (RuntimeException exception) {
            activeConnections.remove(connectionId, managedConnection);
            log.warn("Unable to initiate Alpaca trade-update stream for connection {}", connectionId);
            return CompletableFuture.completedFuture(null);
        }
    }

    @PreDestroy
    void closeAllConnections() {
        activeConnections.keySet().forEach(this::closeConnection);
        bootstrapExecutor.shutdownNow();
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException ignored) {
            // The connection is already being removed from the registry.
        }
    }

    private static final class ManagedConnection {
        private CompletableFuture<WebSocketSession> future;
        private WebSocketSession session;

        synchronized void setFuture(CompletableFuture<WebSocketSession> future) {
            this.future = future;
        }

        synchronized void setSession(WebSocketSession session) {
            this.session = session;
        }

        synchronized void close() {
            if (future != null) {
                future.cancel(true);
            }
            if (session != null) {
                closeQuietly(session);
            }
        }
    }
}
