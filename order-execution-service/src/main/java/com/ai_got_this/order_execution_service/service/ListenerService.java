package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.Order;
import com.ai_got_this.order_execution_service.service.Broker;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class ListenerService {
    private final Broker broker;

    public ListenerService(Broker broker) {
        this.broker = broker;
    }

    @RabbitListener(queues = "approved.orders")
    public void listen(Order order) {
        broker.placeOrder(order);
    }
}
