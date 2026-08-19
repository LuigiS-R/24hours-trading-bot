package com.ai_got_this.order_execution_service.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationConfigurationValidatorTest {
    @Test
    void acceptsACompleteAndSemanticallyValidConfiguration() {
        assertDoesNotThrow(() -> new ApplicationConfigurationValidator(validAlpacaProperties(), validRabbitMqProperties()).validate());
    }

    @Test
    void acceptsAnHttpOrderEndpointForALocalBroker() {
        AlpacaProperties alpacaProperties = validAlpacaProperties();
        alpacaProperties.setOrderEndpoint("http://127.0.0.1:18080/v2/orders");

        assertDoesNotThrow(() -> new ApplicationConfigurationValidator(alpacaProperties, validRabbitMqProperties()).validate());
    }

    @Test
    void rejectsAnInvalidWebSocketUrlBeforeTheApplicationCanStart() {
        AlpacaProperties alpacaProperties = validAlpacaProperties();
        alpacaProperties.setStreamUrl("https://broker.invalid/stream");

        assertThrows(IllegalStateException.class,
                () -> new ApplicationConfigurationValidator(alpacaProperties, validRabbitMqProperties()).validate());
    }

    @Test
    void rejectsAnInvalidSubscriptionBeforeTheApplicationCanStart() {
        AlpacaProperties alpacaProperties = validAlpacaProperties();
        alpacaProperties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"orders\"]}}");

        assertThrows(IllegalStateException.class,
                () -> new ApplicationConfigurationValidator(alpacaProperties, validRabbitMqProperties()).validate());
    }

    private static AlpacaProperties validAlpacaProperties() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setKey("key");
        properties.setSecret("secret");
        properties.setStreamUrl("wss://broker.invalid/stream");
        properties.setSubscriptionMessage("{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}");
        properties.setOrderEndpoint("https://broker.invalid/v2/orders");
        properties.setPaperTradingBaseUrl("https://broker.invalid");
        return properties;
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
