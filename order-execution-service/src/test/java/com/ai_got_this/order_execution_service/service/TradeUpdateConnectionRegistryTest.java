package com.ai_got_this.order_execution_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

class TradeUpdateConnectionRegistryTest {
    private final AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
    private final RecordingWebSocketClient webSocketClient = new RecordingWebSocketClient();
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final TradeUpdateConnectionRegistry registry = new TradeUpdateConnectionRegistry(
            accountServiceClient,
            webSocketClient,
            new ObjectMapper(),
            mock(PublisherService.class),
            properties());

    @AfterEach
    void closeRegistry() {
        registry.closeAllConnections();
    }

    @Test
    void opensStartupConnectionsInBatchesOfTen() throws Exception {
        List<AccountServiceClient.ActiveConnectionCredential> credentials = new ArrayList<>();
        List<CompletableFuture<WebSocketSession>> futures = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            credentials.add(new AccountServiceClient.ActiveConnectionCredential(UUID.randomUUID(), "oauth-" + index));
            CompletableFuture<WebSocketSession> future = new CompletableFuture<>();
            futures.add(future);
            webSocketClient.queuedFutures.add(future);
        }
        when(accountServiceClient.activeConnectionCredentials()).thenReturn(credentials);

        Thread worker = Thread.ofVirtual().start(registry::openInitialConnectionBatches);

        assertThat(webSocketClient.firstBatchStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(webSocketClient.handlers).hasSize(10);
        futures.subList(0, 10).forEach(future -> future.complete(session));
        assertThat(webSocketClient.allBatchesStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(webSocketClient.handlers).hasSize(12);
        futures.subList(10, 12).forEach(future -> future.complete(session));
        worker.join(1_000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(registry.activeConnectionIds()).containsExactlyInAnyOrderElementsOf(
                credentials.stream().map(AccountServiceClient.ActiveConnectionCredential::connectionId).toList());
    }

    @Test
    void openAndCloseEventsManageOnlyTheRequestedConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(accountServiceClient.brokerCredential(connectionId))
                .thenReturn(new AccountServiceClient.BrokerCredential(connectionId, "oauth-token"));
        webSocketClient.completedSession = session;

        registry.openConnection(connectionId);
        registry.closeConnection(connectionId);

        assertThat(webSocketClient.handlers).hasSize(1);
        assertThat(registry.activeConnectionIds()).isEmpty();
        verify(session).close(CloseStatus.NORMAL);
    }

    private static AlpacaProperties properties() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setStreamUrl("wss://broker.invalid/stream");
        properties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}");
        return properties;
    }

    private static final class RecordingWebSocketClient implements WebSocketClient {
        private final List<WebSocketHandler> handlers = Collections.synchronizedList(new ArrayList<>());
        private final List<CompletableFuture<WebSocketSession>> queuedFutures = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch firstBatchStarted = new CountDownLatch(1);
        private final CountDownLatch allBatchesStarted = new CountDownLatch(1);
        private WebSocketSession completedSession;

        @Override
        public CompletableFuture<WebSocketSession> execute(WebSocketHandler handler, String uriTemplate, Object... uriVariables) {
            handlers.add(handler);
            observeBatchSize();
            return nextFuture();
        }

        @Override
        public CompletableFuture<WebSocketSession> execute(
                WebSocketHandler handler,
                WebSocketHttpHeaders headers,
                URI uri) {
            handlers.add(handler);
            observeBatchSize();
            return nextFuture();
        }

        private CompletableFuture<WebSocketSession> nextFuture() {
            synchronized (queuedFutures) {
                return queuedFutures.isEmpty()
                        ? CompletableFuture.completedFuture(completedSession)
                        : queuedFutures.removeFirst();
            }
        }

        private void observeBatchSize() {
            if (handlers.size() == 10) {
                firstBatchStarted.countDown();
            }
            if (handlers.size() == 12) {
                allBatchesStarted.countDown();
            }
        }
    }
}
