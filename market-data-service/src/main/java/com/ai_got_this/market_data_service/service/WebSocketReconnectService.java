package com.ai_got_this.market_data_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebSocketReconnectService {
    private static final long SESSION_CLOSE_TIMEOUT = TimeUnit.SECONDS.toNanos(30);

    private final ObjectProvider<WebSocketConnectionManager> connectionManagerProvider;

    private int consecutiveAttempts = 0;

    public WebSocketReconnectService(ObjectProvider<WebSocketConnectionManager> connectionManagerProvider) {
        this.connectionManagerProvider = connectionManagerProvider;
    }

    public void reconnect(WebSocketSession session, CloseStatus status) {
        log.warn("WebSocket connection closed with status: {}. Attempting to reconnect...", status);
        long waitStartedAt = System.nanoTime();

        try {
            while (session.isOpen()) {
                long elapsedNanos = System.nanoTime() - waitStartedAt;
                if (elapsedNanos >= SESSION_CLOSE_TIMEOUT) {
                    log.warn("Timed out waiting 30 seconds for the WebSocket session to close");
                    return;
                }
            }

            WebSocketConnectionManager connectionManager = connectionManagerProvider.getIfAvailable();
            if (connectionManager == null) {
                log.error("Alpaca WebSocket connection manager is not available");
                return;
            }
            if (connectionManager.isConnected()) {
                log.warn("Alpaca WebSocket is already connected, but a reconnect was requested.");
                return;
            }
            if (consecutiveAttempts > 5) {
                log.error("Alpaca WebSocket has failed to reconnect more than 5 times. Please check your configuration and network connection.");
                return;
            }

            int reconnectDelay = getDelayForReconnect(consecutiveAttempts);
            Thread.sleep(reconnectDelay);
            connectionManager.stop();
            connectionManager.start();
            consecutiveAttempts++;
        }
        catch (InterruptedException e) {
            log.error("Reconnect attempt interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public void onSuccessfulReconnection() {
        consecutiveAttempts = 0;
    }

    private int getDelayForReconnect(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return 0;
        }

        return switch (consecutiveFailures) {
            case 1 -> 10_000;
            case 2 -> 20_000;
            case 3 -> 30_000;
            case 4 -> 60_000;
            default -> 300_000;
        };
    }
}
