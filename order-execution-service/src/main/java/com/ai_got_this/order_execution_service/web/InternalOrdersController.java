package com.ai_got_this.order_execution_service.web;

import com.ai_got_this.order_execution_service.domain.OrderProjection;
import com.ai_got_this.order_execution_service.service.OrderReconciliationService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/accounts/{connectionId}/orders")
public class InternalOrdersController {
    private final OrderReconciliationService orderReconciliationService;

    public InternalOrdersController(OrderReconciliationService orderReconciliationService) {
        this.orderReconciliationService = orderReconciliationService;
    }

    @GetMapping
    public List<OrderProjection> latestOrders(@PathVariable UUID connectionId) {
        return orderReconciliationService.latestOrders(connectionId);
    }
}
