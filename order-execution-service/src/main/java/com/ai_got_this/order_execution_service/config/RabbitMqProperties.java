package com.ai_got_this.order_execution_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("rabbitmq.assets")
@Data
public class RabbitMqProperties {
    private String ordersApprovedQueue;
    private String orderEventsPortfolioQueue;
    private String orderEventsNotificationQueue;
    private String orderEventsExchange;
}
