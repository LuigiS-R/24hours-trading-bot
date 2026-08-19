package com.ai_got_this.order_execution_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_service.domain.OrderProjection;
import com.ai_got_this.order_execution_service.service.OrderReconciliationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalOrdersControllerTest {
    @Mock
    private OrderReconciliationService orderReconciliationService;

    @Test
    void latestOrdersDelegatesToTheTwentyNewestProjectionQuery() {
        UUID connectionId = UUID.randomUUID();
        OrderProjection order = new OrderProjection();
        order.setConnectionId(connectionId);
        when(orderReconciliationService.latestOrders(connectionId)).thenReturn(List.of(order));

        List<OrderProjection> result = new InternalOrdersController(orderReconciliationService).latestOrders(connectionId);

        assertThat(result).containsExactly(order);
        verify(orderReconciliationService).latestOrders(connectionId);
    }
}
