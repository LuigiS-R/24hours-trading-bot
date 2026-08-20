package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.RabbitMqProperties;
import com.ai_got_this.order_execution_service.dto.Order;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

class ListenerServiceTest {
    @Test
    void forwardsEachApprovedOrderToTheBrokerExactlyOnce() {
        Broker broker = mock(Broker.class);
        ListenerService listener = new ListenerService(broker, new OrderValidator(), rabbitMqProperties());
        Order order = validOrder();

        listener.listen(order);

        verify(broker).placeOrder(order);
    }

    @Test
    void rejectsAZeroQuantityOrderWithoutSubmittingItToTheBroker() {
        Broker broker = mock(Broker.class);
        ListenerService listener = new ListenerService(broker, new OrderValidator(), rabbitMqProperties());
        Order order = validOrder();
        order.setQuantity(0);

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            listener.listen(order);
        }
        finally {
            detachAppender(appender);
        }

        verifyNoInteractions(broker);
        assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("quantity must be greater than zero")));
    }

    private static Order validOrder() {
        Order order = new Order();
        order.setConnectionId("cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64");
        order.setSymbol("AAPL");
        order.setQuantity(1);
        order.setSide("buy");
        order.setType("limit");
        order.setLimitPrice(BigDecimal.valueOf(100));
        order.setReason("Approved trading signal");
        return order;
    }

    private static RabbitMqProperties rabbitMqProperties() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setOrdersApprovedQueue("orders.approved");
        return properties;
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ListenerService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(ListenerService.class)).detachAppender(appender);
    }
}
