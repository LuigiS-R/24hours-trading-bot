package com.ai_got_this.order_execution_service.config;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateResponse;
import com.ai_got_this.order_execution_service.service.PublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;

@Component
@Slf4j
public class AlpacaWebSocketHandler extends BinaryWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final PublisherService publisherService;
    private final AlpacaProperties alpacaProperties;
    private final AlpacaWebSocketReconnectSupervisor reconnectSupervisor;

    public AlpacaWebSocketHandler(ObjectMapper objectMapper, PublisherService publisherService, AlpacaProperties alpacaProperties, AlpacaWebSocketReconnectSupervisor reconnectSupervisor) {
        this.publisherService = publisherService;
        this.objectMapper = objectMapper;
        this.alpacaProperties = alpacaProperties;
        this.reconnectSupervisor = reconnectSupervisor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String subscriptionMessage = alpacaProperties.getSubscriptionMessage();

        log.info("Connected to Alpaca WebSocket server");
        reconnectSupervisor.onConnectionEstablished();
        log.info("Subscribing to trade updates stream...");

        session.sendMessage(new TextMessage(subscriptionMessage));
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
                    processMessage(alpacaMessage);
                }
            } else {
                processMessage(rootNode);
            }
        }
        catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        reconnectSupervisor.onConnectionClosed(status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        reconnectSupervisor.onTransportError(exception);
    }

    private void processMessage(JsonNode alpacaMessage) {
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
                    log.info("Successfully authenticated with Alpaca WebSocket server");
                } else {
                    log.error("Failed to authenticate with Alpaca WebSocket server: {}", alpacaMessage.path("data"));
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
                publisherService.publishOrderEvent(new OrderEvent(response));
                return;
            }

            log.warn("Received unknown message type from Alpaca WebSocket: {}", alpacaMessage);
        }
        catch (Exception e) {
            log.error("Error processing Alpaca message: {}", e.getMessage());
        }
    }
}
