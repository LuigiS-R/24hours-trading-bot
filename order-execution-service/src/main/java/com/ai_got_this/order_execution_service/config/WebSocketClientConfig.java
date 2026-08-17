package com.ai_got_this.order_execution_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WebSocketClientConfig {
    private AlpacaProperties alpacaProperties;

    public WebSocketClientConfig(AlpacaProperties alpacaProperties, ApplicationConfigurationValidator configurationValidator) {
        this.alpacaProperties = alpacaProperties;
    }

    @Bean
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        String streamUrl = alpacaProperties.getStreamUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", alpacaProperties.getKey());
        headers.add("APCA-API-SECRET-KEY", alpacaProperties.getSecret());
        headers.add("Content-Type", "application/msgpack");

        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                alpacaWebSocketHandler,
                streamUrl
        );
        manager.setAutoStartup(true);
        manager.setHeaders(headers);
        return manager;
    }
}
