package com.ai_got_this.market_data_service.config;

import com.ai_got_this.market_data_service.dto.AlpacaBarMessage;
import com.ai_got_this.market_data_service.dto.AlpacaErrorMessage;
import com.ai_got_this.market_data_service.dto.AlpacaMessage;
import com.ai_got_this.market_data_service.dto.AlpacaUnknownMessage;
import com.ai_got_this.market_data_service.dto.MarketData;
import com.ai_got_this.market_data_service.exception.ErrorMarketDataMessageException;
import com.ai_got_this.market_data_service.service.MarketDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
@Slf4j
public class AlpacaWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final MarketDataPublisher marketDataPublisher;
    private final AlpacaProperties alpacaProperties;

    public AlpacaWebSocketHandler(
            ObjectMapper objectMapper,
            MarketDataPublisher marketDataPublisher,
            AlpacaProperties alpacaProperties
    ) {
        this.objectMapper = objectMapper;
        this.marketDataPublisher = marketDataPublisher;
        this.alpacaProperties = alpacaProperties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String subscriptionMessage = alpacaProperties.getSubscriptionMessage();
        log.info("Connected to Alpaca WebSocket server");
        log.info("Subscribing to market data stream...");
        session.sendMessage(new TextMessage(subscriptionMessage));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            AlpacaMessage alpacaMessage = objectMapper.readValue(message.getPayload(), AlpacaMessage[].class)[0];

            if (alpacaMessage instanceof AlpacaErrorMessage errorMessage) {
                Integer errorCode = errorMessage.getCode();
                String errorMsg = errorMessage.getMsg();

                log.error("Received error message from Alpaca's websocket: code: {} msg: {}", errorCode, errorMsg);
            }

            if (alpacaMessage instanceof AlpacaBarMessage barMessage) {
                MarketData marketData = MarketData.builder()
                        .ticket(barMessage.getSymbol())
                        .open(barMessage.getOpen())
                        .high(barMessage.getHigh())
                        .low(barMessage.getLow())
                        .close(barMessage.getClose())
                        .volume(barMessage.getVolume())
                        .build();

                marketDataPublisher.publishMarketData("market-data-exchange", marketData, "market-data");
            }

            if (alpacaMessage instanceof AlpacaUnknownMessage unknownMessage) {
                log.warn("Received unknown message from Alpaca's websocket: type: {} {}", unknownMessage.getType(), unknownMessage);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
