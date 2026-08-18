package com.ai_got_this.order_execution_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class WebSocketClientConfigTest {
    @Test
    void createsAConnectionManagerForACompleteTradeUpdateConfiguration() {
        AlpacaProperties properties = validProperties();

        WebSocketConnectionManager manager = new WebSocketClientConfig(properties, validator(properties))
                .webConnectionManager(mock(AlpacaWebSocketHandler.class));

        assertNotNull(manager);
        assertNotNull(manager.getHeaders());
        org.junit.jupiter.api.Assertions.assertEquals("key", manager.getHeaders().getFirst("APCA-API-KEY-ID"));
        org.junit.jupiter.api.Assertions.assertEquals("secret", manager.getHeaders().getFirst("APCA-API-SECRET-KEY"));
        org.junit.jupiter.api.Assertions.assertEquals("application/msgpack", manager.getHeaders().getFirst("Content-Type"));
    }

    private static AlpacaProperties validProperties() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setKey("key");
        properties.setSecret("secret");
        properties.setStreamUrl("wss://broker.invalid/stream");
        properties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}");
        return properties;
    }

    private static ApplicationConfigurationValidator validator(AlpacaProperties alpacaProperties) {
        return new ApplicationConfigurationValidator(alpacaProperties, validRabbitMqProperties());
    }

    private static RabbitMqProperties validRabbitMqProperties() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setOrdersApprovedQueue("orders.approved");
        properties.setOrderEventsPortfolioQueue("order-events-portfolio");
        properties.setOrderEventsNotificationQueue("order-events-notification");
        properties.setOrderEventsExchange("order-events-fanout");
        return properties;
    }
}
