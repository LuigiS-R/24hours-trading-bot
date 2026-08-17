package com.ai_got_this.order_execution_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

@Configuration
@Slf4j
public class WebSocketClientConfig {
    private AlpacaProperties alpacaProperties;

    public WebSocketClientConfig(AlpacaProperties alpacaProperties) {
        this.alpacaProperties = alpacaProperties;
    }

    @Bean
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        String apiKeyId = alpacaProperties.getKey();
        String apiSecretKey = alpacaProperties.getSecret();
        String streamUrl = alpacaProperties.getStreamUrl();
        if (!isValidConfiguration(apiKeyId, apiSecretKey, streamUrl, alpacaProperties.getSubscriptionMessage())) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", apiKeyId);
        headers.add("APCA-API-SECRET-KEY", apiSecretKey);
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

    private boolean isValidConfiguration(String apiKeyId, String apiSecretKey, String streamUrl, String subscriptionMessage) {
        if (apiKeyId == null || apiKeyId.isBlank() || apiSecretKey == null || apiSecretKey.isBlank()) {
            log.error("Alpaca WebSocket credentials are not configured");
            return false;
        }

        if (streamUrl == null || streamUrl.isBlank()) {
            log.error("Alpaca WebSocket trade stream URL is not configured");
            return false;
        }

        try {
            String scheme = URI.create(streamUrl).getScheme();
            if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
                log.error("Alpaca WebSocket trade stream URL must use ws or wss");
                return false;
            }
        }
        catch (IllegalArgumentException e) {
            log.error("Alpaca WebSocket trade stream URL is invalid");
            return false;
        }

        if (subscriptionMessage == null || subscriptionMessage.isBlank()) {
            log.error("Alpaca WebSocket subscription message is not configured");
            return false;
        }

        try {
            JsonNode subscription = new ObjectMapper().readTree(subscriptionMessage);
            if (!subscription.isObject()) {
                log.error("Alpaca WebSocket subscription message must be a JSON object");
                return false;
            }
            if (!"listen".equals(subscription.path("action").asText())
                    || !subscription.path("data").path("streams").isArray()
                    || !containsTradeUpdates(subscription.path("data").path("streams"))) {
                log.error("Alpaca WebSocket subscription message must listen to trade_updates");
                return false;
            }
        }
        catch (Exception e) {
            log.error("Alpaca WebSocket subscription message is invalid");
            return false;
        }

        return true;
    }

    private boolean containsTradeUpdates(JsonNode streams) {
        for (JsonNode stream : streams) {
            if ("trade_updates".equals(stream.asText())) {
                return true;
            }
        }
        return false;
    }
}
