package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.RabbitMqProperties;
import com.ai_got_this.order_execution_service.dto.ConnectionEvent;
import com.ai_got_this.order_execution_service.dto.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ListenerService {
    private final Broker broker;
    private final OrderValidator orderValidator;
    private final RabbitMqProperties rabbitMqProperties;
    private final TradeUpdateConnectionRegistry tradeUpdateConnectionRegistry;

    public ListenerService(
            Broker broker,
            OrderValidator orderValidator,
            RabbitMqProperties rabbitMqProperties,
            TradeUpdateConnectionRegistry tradeUpdateConnectionRegistry) {
        this.broker = broker;
        this.orderValidator = orderValidator;
        this.rabbitMqProperties = rabbitMqProperties;
        this.tradeUpdateConnectionRegistry = tradeUpdateConnectionRegistry;
    }

    @RabbitListener(queues = "#{rabbitMqProperties.ordersApprovedQueue}")
    public void listen(Order order) {
        var rejectionReason = orderValidator.validate(order);
        if (rejectionReason.isPresent()) {
            log.warn("Rejected order before broker submission: {}", rejectionReason.get());
            return;
        }
        broker.placeOrder(order);
    }

    @RabbitListener(queues = "#{rabbitMqProperties.connectionEventsQueue}")
    public void listenConnectionEvent(ConnectionEvent event) {
        if (event == null || event.getConnectionId() == null || event.getType() == null) {
            log.warn("Ignoring invalid connection event.");
            return;
        }

        switch (event.getType().trim().toLowerCase(java.util.Locale.ROOT)) {
            case "open" -> tradeUpdateConnectionRegistry.openConnection(event.getConnectionId());
            case "close" -> tradeUpdateConnectionRegistry.closeConnection(event.getConnectionId());
            default -> log.warn("Ignoring connection event with unsupported type {}", event.getType());
        }
    }
}
