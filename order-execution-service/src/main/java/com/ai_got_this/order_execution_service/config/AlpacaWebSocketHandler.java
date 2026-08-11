package com.ai_got_this.order_execution_service.config;

import com.ai_got_this.order_execution_service.dto.ErrorMessage;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateResponse;
import com.ai_got_this.order_execution_service.service.PublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Slf4j
public class AlpacaWebSocketHandler extends BinaryWebSocketHandler {
    private ObjectMapper objectMapper;
    private PublisherService publisherService;
    private AlpacaProperties alpacaProperties;
    private WebSocketConnectionManager webSocketConnectionManager;
    private ScheduledExecutorService scheduler;

    public AlpacaWebSocketHandler(ObjectMapper objectMapper, PublisherService publisherService, AlpacaProperties alpacaProperties, @Lazy WebSocketConnectionManager webSocketConnectionManager, ScheduledExecutorService scheduler) {
        this.publisherService = publisherService;
        this.objectMapper = objectMapper;
        this.alpacaProperties = alpacaProperties;
        this.webSocketConnectionManager = webSocketConnectionManager;
        this.scheduler = scheduler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String subscriptionMessage = alpacaProperties.getSubscriptionMessage();

        log.info("Connected to Alpaca WebSocket server");
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

            if (rootNode.has("action") && "error".equals(rootNode.get("action").asText())) {
                ErrorMessage errorMessage = objectMapper.treeToValue(rootNode, ErrorMessage.class);
                log.error("Error Message Received from Alpaca WebSocket: {}", errorMessage.getData().getErrorMessage());
            }
            else if (rootNode.has("stream") && "trade_updates".equals(rootNode.get("stream").asText())){
                TradeUpdateResponse response = objectMapper.treeToValue(rootNode, TradeUpdateResponse.class);
                OrderEvent orderEvent = new OrderEvent(response);
                publisherService.publishOrderEvent(orderEvent);
            }
            else{
                log.warn("Received unknown message type from Alpaca WebSocket: {}", rootNode);
            }
        }
        catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("WebSocket connection closed: {}", status);
        log.info("Attempting to reconnect to Alpaca WebSocket server...");
        scheduler.schedule(() -> {
            webSocketConnectionManager.stop();
            webSocketConnectionManager.start();
        }, 5, java.util.concurrent.TimeUnit.SECONDS);
    }
}