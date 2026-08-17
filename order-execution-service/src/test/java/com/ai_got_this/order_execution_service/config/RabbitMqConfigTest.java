package com.ai_got_this.order_execution_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqConfigTest {
    @Test
    void createsRabbitAssetsUsingConfiguredNames() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setOrderEventsPortfolioQueue("portfolio-events");
        properties.setOrderEventsNotificationQueue("notification-events");
        properties.setOrderEventsExchange("order-events");
        RabbitMqConfig config = new RabbitMqConfig(properties, new ApplicationConfigurationValidator(validAlpacaProperties(), properties));

        Queue portfolioQueue = config.orderEventsPortfolioQueue();
        Queue notificationQueue = config.orderEventsNotificationQueue();
        FanoutExchange exchange = config.orderEventsFanoutExchange();

        assertEquals("portfolio-events", portfolioQueue.getName());
        assertEquals("notification-events", notificationQueue.getName());
        assertEquals("order-events", exchange.getName());
        assertTrue(portfolioQueue.isDurable());
        assertTrue(notificationQueue.isDurable());
    }

    private static AlpacaProperties validAlpacaProperties() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setKey("key");
        properties.setSecret("secret");
        properties.setStreamUrl("wss://broker.invalid/stream");
        properties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}");
        properties.setOrderEndpoint("https://broker.invalid/v2/orders");
        return properties;
    }
}
