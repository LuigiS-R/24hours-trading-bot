package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.RabbitMqProperties;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublisherServiceTest {
    @Test
    void publishesOrderEventsToTheConfiguredFanoutExchange() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        PublisherService publisher = new PublisherService(rabbitTemplate, rabbitMqProperties());
        OrderEvent event = new OrderEvent();
        event.setEvent("fill");

        publisher.publishOrderEvent(event);

        verify(rabbitTemplate).convertAndSend("order-events-fanout", "", event);
    }

    @Test
    void propagatesPublicationFailuresWithoutClaimingSuccess() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        PublisherService publisher = new PublisherService(rabbitTemplate, rabbitMqProperties());
        OrderEvent event = new OrderEvent();
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend("order-events-fanout", "", event);

        assertThrows(AmqpException.class, () -> publisher.publishOrderEvent(event));
    }

    private static RabbitMqProperties rabbitMqProperties() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setOrderEventsExchange("order-events-fanout");
        return properties;
    }
}
