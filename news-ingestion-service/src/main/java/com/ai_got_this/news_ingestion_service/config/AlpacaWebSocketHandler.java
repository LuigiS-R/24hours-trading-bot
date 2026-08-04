package com.ai_got_this.news_ingestion_service.config;

import com.ai_got_this.news_ingestion_service.dto.AlpacaMessage;
import com.ai_got_this.news_ingestion_service.dto.NewsHeadline;
import com.ai_got_this.news_ingestion_service.exception.ErrorAlpacaMessageException;
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

    public AlpacaWebSocketHandler(ObjectMapper objectMapper, NewsPublisher newsPublisher) {
        this.objectMapper = objectMapper;
        this.newsPublisher = newsPublisher;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        System.out.println("Connected to Alpaca WebSocket server");
        System.out.println("Subscribing to news stream...");
        session.sendMessage(new TextMessage("{\"action\": \"subscribe\", \"news\": [\"*\"], \"symbols\": [\"*\"]}"));
    }



    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message){
        try{
            AlpacaMessage alpacaMessage = objectMapper.readValue(message.getPayload(), AlpacaMessage[].class)[0];

            if (alpacaMessage.getType().equals("error")) {
                Integer errorCode = alpacaMessage.getCode();
                String errorMessage = alpacaMessage.getMsg();

                throw new ErrorAlpacaMessageException("Error [" + errorCode + "]: " + errorMessage);
            }

            if (alpacaMessage.getType().equals("n")) {
                //Handle news message
                List<String> relatedSymbols = alpacaMessage.getSymbols();
                for(String symbol: relatedSymbols){
                    NewsHeadline newsHeadline = NewsHeadline.builder()
                            .ticket(symbol)
                            .headline(alpacaMessage.getHeadline())
                            .build();

                    newsPublisher.publishNews("financial-news-exchange", newsHeadline, "financial-news");
                }
            }
        }
        catch(ErrorAlpacaMessageException e){
            log.error(e.getMessage());
        }
        catch(Exception e){
            log.error(e.getMessage());
        }
    }
}
