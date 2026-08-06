package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublisherService {
    private final RabbitTemplate rabbitTemplate;

    public PublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderEvent(OrderEvent message) {
        rabbitTemplate.convertAndSend("order-events-fanout", "", message);
    }
}
