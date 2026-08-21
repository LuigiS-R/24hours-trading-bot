package com.ai_got_this.order_execution_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.client.WebSocketClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class WebSocketClientConfigTest {
    @Test
    void createsAReusableClientForAccountScopedTradeUpdateConnections() {
        AlpacaProperties properties = validProperties();

        WebSocketClient client = new WebSocketClientConfig(properties, validator(properties))
                .alpacaWebSocketClient();

        assertNotNull(client);
    }

    private static AlpacaProperties validProperties() {
        AlpacaProperties properties = new AlpacaProperties();
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
        properties.setOrderEventsPersisterQueue("order-events-persister");
        properties.setOrderEventsExchange("order-events-fanout");
        properties.setConnectionEventsQueue("connection.events");
        return properties;
    }
}
