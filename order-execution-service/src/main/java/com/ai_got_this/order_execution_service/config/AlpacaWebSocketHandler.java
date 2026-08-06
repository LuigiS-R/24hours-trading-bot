package com.ai_got_this.order_execution_service.config;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateResponse;
import com.ai_got_this.order_execution_service.service.PublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class AlpacaWebSocketHandler extends TextWebSocketHandler {
    private ObjectMapper objectMapper;
    private PublisherService publisherService;

    public AlpacaWebSocketHandler(ObjectMapper objectMapper, PublisherService publisherService) {
        this.publisherService = publisherService;
        this.objectMapper = objectMapper;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        System.out.println("Connected to Alpaca WebSocket server");
        System.out.println("Subscribing to trade updates stream...");
        session.sendMessage(new TextMessage("{\"action\": \"listen\", \"data\": {\"streams\": [\"trade_updates\"]}}"));
    }



    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message){
        try {
            String payload = message.getPayload();
            TradeUpdateResponse response = objectMapper.readValue(payload, TradeUpdateResponse.class);
            OrderEvent orderEvent = new OrderEvent(response);
            publisherService.publishOrderEvent(orderEvent);
        }
        catch (Exception e) {
            log.error("Error processing message: ", e);
        }
    }
}