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
    private final String alpacaAPIId = "";
    private final String alpacaAPIKey = "";

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Profile("!test")
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", alpacaAPIId);
        headers.add("APCA-API-SECRET-KEY", alpacaAPIKey);

        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                alpacaWebSocketHandler,
                "wss://stream.data.alpaca.markets/v2/iex"
        );
        manager.setAutoStartup(true);
        manager.setHeaders(headers);
        return manager;
    }
}
