package com.ai_got_this.market_data_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WebSocketClientConfig {
    private final AlpacaProperties alpacaProperties;

    public WebSocketClientConfig(AlpacaProperties alpacaProperties) {
        this.alpacaProperties = alpacaProperties;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        String apiKeyId = alpacaProperties.getKey();
        String apiSecretKey = alpacaProperties.getSecret();
        String marketStreamUrl = alpacaProperties.getMarketStreamUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", apiKeyId);
        headers.add("APCA-API-SECRET-KEY", apiSecretKey);

        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                alpacaWebSocketHandler,
                marketStreamUrl
        );
        manager.setAutoStartup(true);
        manager.setHeaders(headers);
        return manager;
    }
}
