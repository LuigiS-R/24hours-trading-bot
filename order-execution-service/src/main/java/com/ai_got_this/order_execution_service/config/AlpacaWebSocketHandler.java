package com.ai_got_this.order_execution_service.config;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateResponse;
import com.ai_got_this.order_execution_service.service.PublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AlpacaWebSocketHandler extends BinaryWebSocketHandler {
    private final UUID connectionId;
    private final String oauthToken;
    private final ObjectMapper objectMapper;
    private final PublisherService publisherService;
    private final AlpacaProperties alpacaProperties;
    private final Runnable closedCallback;
    private final AtomicBoolean subscribed = new AtomicBoolean();

    public AlpacaWebSocketHandler(
            UUID connectionId,
            String oauthToken,
            ObjectMapper objectMapper,
            PublisherService publisherService,
            AlpacaProperties alpacaProperties,
            Runnable closedCallback) {
        this.connectionId = connectionId;
        this.oauthToken = oauthToken;
        this.publisherService = publisherService;
        this.objectMapper = objectMapper;
        this.alpacaProperties = alpacaProperties;
        this.closedCallback = closedCallback;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        log.info("Connected to Alpaca trade-update WebSocket for connection {}", connectionId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "action", "authenticate",
                "data", Map.of("oauth_token", oauthToken)))));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message){
        try {
            ByteBuffer payload = message.getPayload();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);

            JsonNode rootNode = objectMapper.readTree(bytes);

            if (rootNode.isArray()) {
                for (JsonNode alpacaMessage : rootNode) {
                    processMessage(session, alpacaMessage);
                }
            } else {
                processMessage(session, rootNode);
            }
        }
        catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("Alpaca trade-update WebSocket closed for connection {}: {}", connectionId, status);
        closedCallback.run();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Alpaca trade-update WebSocket transport error for connection {}: {}", connectionId, exception.getMessage());
        try {
            session.close();
        } catch (IOException ignored) {
            // The transport has already failed; the registry still owns reconnect policy.
        }
        closedCallback.run();
    }

    private void processMessage(WebSocketSession session, JsonNode alpacaMessage) {
        try {
            if ("error".equals(alpacaMessage.path("action").asText())) {
                log.error("Error message received from Alpaca WebSocket: {}",
                        alpacaMessage.path("data").path("error_message").asText());
                return;
            }

            if ("error".equals(alpacaMessage.path("T").asText())) {
                log.error("Received error message from Alpaca WebSocket: code: {} msg: {}",
                        alpacaMessage.path("code").asText(), alpacaMessage.path("msg").asText());
                return;
            }

            if ("success".equals(alpacaMessage.path("T").asText())
                    && "authenticated".equals(alpacaMessage.path("msg").asText())) {
                log.info("Successfully authenticated with Alpaca WebSocket server");
                return;
            }

            if ("subscription".equals(alpacaMessage.path("T").asText())) {
                log.info("Alpaca WebSocket subscription acknowledged: {}", alpacaMessage);
                return;
            }

            String stream = alpacaMessage.path("stream").asText();
            if ("authorization".equals(stream)) {
                String status = alpacaMessage.path("data").path("status").asText();
                if ("authorized".equals(status) || "authenticated".equals(status)) {
                    subscribe(session);
                } else {
                    log.error("Failed to authenticate Alpaca trade-update WebSocket for connection {}", connectionId);
                }
                return;
            }

            if ("listening".equals(stream)) {
                log.info("Alpaca WebSocket subscription acknowledged for streams: {}",
                        alpacaMessage.path("data").path("streams"));
                return;
            }

            if ("trade_updates".equals(stream)) {
                TradeUpdateResponse response = objectMapper.treeToValue(alpacaMessage, TradeUpdateResponse.class);
                OrderEvent event = new OrderEvent(response);
                event.setConnectionId(connectionId.toString());
                publisherService.publishOrderEvent(event);
                return;
            }

            log.warn("Received unknown message type from Alpaca WebSocket: {}", alpacaMessage);
        }
        catch (Exception e) {
            log.error("Error processing Alpaca message: {}", e.getMessage());
        }
    }

    private void subscribe(WebSocketSession session) throws IOException {
        if (subscribed.compareAndSet(false, true)) {
            session.sendMessage(new TextMessage(alpacaProperties.getSubscriptionMessage()));
            log.info("Subscribed to Alpaca trade updates for connection {}", connectionId);
        }
    }
}
