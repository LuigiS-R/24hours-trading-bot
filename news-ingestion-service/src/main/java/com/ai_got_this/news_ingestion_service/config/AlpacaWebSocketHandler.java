package com.ai_got_this.news_ingestion_service.config;

import com.ai_got_this.news_ingestion_service.dto.*;
import com.ai_got_this.news_ingestion_service.service.NewsPublisher;
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
public class AlpacaWebSocketHandler extends TextWebSocketHandler{
    private ObjectMapper objectMapper;
    private NewsPublisher newsPublisher;
    private AlpacaProperties alpacaProperties;
    private AlpacaWebSocketReconnectSupervisor reconnectSupervisor;

    public AlpacaWebSocketHandler(ObjectMapper objectMapper, NewsPublisher newsPublisher, AlpacaProperties alpacaProperties, AlpacaWebSocketReconnectSupervisor reconnectSupervisor) {
        this.objectMapper = objectMapper;
        this.newsPublisher = newsPublisher;
        this.alpacaProperties = alpacaProperties;
        this.reconnectSupervisor = reconnectSupervisor;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String subscriptionMessage = alpacaProperties.getSubscriptionMessage();
        log.info("Connected to Alpaca WebSocket server");
        reconnectSupervisor.onConnectionEstablished();
        log.info("Subscribing to news stream...");
        session.sendMessage(new TextMessage(subscriptionMessage));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        reconnectSupervisor.onConnectionClosed(status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        reconnectSupervisor.onTransportError(exception);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message){
        try{
            AlpacaMessage[] alpacaMessages = objectMapper.readValue(message.getPayload(), AlpacaMessage[].class);
            for (AlpacaMessage alpacaMessage : alpacaMessages) {
                processMessage(alpacaMessage);
            }
        }
        catch(Exception e){
            log.error(e.getMessage());
        }
    }

    private void processMessage(AlpacaMessage alpacaMessage) {
        try {

            if (alpacaMessage instanceof AlpacaErrorMessage errorMessage) {
                Integer errorCode = errorMessage.getCode();
                String errorMsg = errorMessage.getMsg();

                if (errorCode != null && (errorCode == 401 || errorCode == 402 || errorCode == 404)) {
                    log.error("Failed to authenticate with Alpaca WebSocket server: code: {} msg: {}", errorCode, errorMsg);
                }
                else {
                    log.error("Received error message from Alpaca's websocket: code: {} msg: {}", errorCode, errorMsg);
                }
            }

            if (alpacaMessage instanceof AlpacaSuccessMessage successMessage) {
                if ("authenticated".equals(successMessage.getMsg())) {
                    log.info("Successfully authenticated with Alpaca WebSocket server");
                }
            }

            if (alpacaMessage instanceof AlpacaNewsMessage alpacaNewsMessage){
                List<String> relatedSymbols = alpacaNewsMessage.getSymbols();
                for(String symbol: relatedSymbols){
                    NewsHeadline newsHeadline = NewsHeadline.builder()
                            .ticket(symbol)
                            .headline(alpacaNewsMessage.getHeadline())
                            .build();

                    newsPublisher.publishNews("financial-news-exchange", newsHeadline, "financial-news");
                }
            }

            if (alpacaMessage instanceof AlpacaUnknownMessage unknownMessage) {
                log.warn("Received unknown message from Alpaca's websocket: type: {} {}", unknownMessage.getType(), unknownMessage);
            }
        }
        catch(Exception e){
            log.error(e.getMessage());
        }
    }
}
