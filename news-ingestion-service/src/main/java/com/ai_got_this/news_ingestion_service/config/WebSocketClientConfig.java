package com.ai_got_this.news_ingestion_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WebSocketClientConfig {
    private AlpacaProperties alpacaProperties;

    public WebSocketClientConfig(AlpacaProperties alpacaProperties) {
        this.alpacaProperties = alpacaProperties;
    }

    @Bean
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        //Create a new HttpHeaders object and add both the alpacaAPI ID and alpacaAPIKey to it
        String apiKeyId = alpacaProperties.getKey();
        String apiSecretKey = alpacaProperties.getSecret();
        String newsStreamUrl = alpacaProperties.getNewsStreamUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", apiKeyId);
        headers.add("APCA-API-SECRET-KEY", apiSecretKey);

        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                alpacaWebSocketHandler,
                newsStreamUrl
        );

        manager.setAutoStartup(true);
        manager.setHeaders(headers);
        return manager;
    }
}
