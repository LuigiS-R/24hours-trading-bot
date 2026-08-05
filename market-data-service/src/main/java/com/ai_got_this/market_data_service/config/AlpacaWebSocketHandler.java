package com.ai_got_this.market_data_service.config;

import com.ai_got_this.market_data_service.dto.AlpacaMessage;
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
import java.util.List;

@Component
@Slf4j
public class AlpacaWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final MarketDataPublisher marketDataPublisher;

    public AlpacaWebSocketHandler(ObjectMapper objectMapper, MarketDataPublisher marketDataPublisher) {
        this.objectMapper = objectMapper;
        this.marketDataPublisher = marketDataPublisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        System.out.println("Connected to Alpaca WebSocket server");
        System.out.println("Subscribing to market data stream...");
        session.sendMessage(new TextMessage("{\"action\":\"subscribe\",\"bars\":[\"*\"]}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            AlpacaMessage alpacaMessage = objectMapper.readValue(message.getPayload(), AlpacaMessage[].class)[0];

            if (alpacaMessage.getType().equals("error")) {
                Integer errorCode = alpacaMessage.getCode();
                String errorMessage = alpacaMessage.getMsg();

                throw new ErrorMarketDataMessageException("Error [" + errorCode + "]: " + errorMessage);
            }

            if (alpacaMessage.getType().equals("b")) {
                MarketData marketData = MarketData.builder()
                        .ticket(alpacaMessage.getSymbol())
                        .open(alpacaMessage.getOpen())
                        .high(alpacaMessage.getHigh())
                        .low(alpacaMessage.getLow())
                        .close(alpacaMessage.getClose())
                        .volume(alpacaMessage.getVolume())
                        .build();

                marketDataPublisher.publishMarketData("market-data-exchange", marketData, "market-data");
            }
        } catch (ErrorMarketDataMessageException e) {
            log.error(e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
