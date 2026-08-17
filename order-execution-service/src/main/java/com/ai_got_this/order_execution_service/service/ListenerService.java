package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ListenerService {
    private final Broker broker;
    private final OrderValidator orderValidator;

    public ListenerService(Broker broker, OrderValidator orderValidator) {
        this.broker = broker;
        this.orderValidator = orderValidator;
    }

    @RabbitListener(queues = "orders.approved")
    public void listen(Order order) {
        var rejectionReason = orderValidator.validate(order);
        if (rejectionReason.isPresent()) {
            log.warn("Rejected order before broker submission: {}", rejectionReason.get());
            return;
        }
        broker.placeOrder(order);
    }
}
