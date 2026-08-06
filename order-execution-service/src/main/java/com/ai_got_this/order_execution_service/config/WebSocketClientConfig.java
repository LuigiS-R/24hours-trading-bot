package com.ai_got_this.order_execution_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WebSocketClientConfig {
    private final String alpacaAPIId = "";
    private final String alpacaAPIKey = "";

    @Bean
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        //Create a new HttpHeaders object and add both the alpacaAPI ID and alpacaAPIKey to it
        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", alpacaAPIId);
        headers.add("APCA-API-SECRET-KEY", alpacaAPIKey);

        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                alpacaWebSocketHandler,
                "wss://paper-api.alpaca.markets/stream"
        );
        manager.setAutoStartup(true);
        manager.setHeaders(headers);
        return manager;
    }
}
