package com.ai_got_this.order_execution_persister.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.dto.OrderEvent;
import com.ai_got_this.order_execution_persister.dto.TradeUpdateOrder;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventPersistenceServiceTest {
    @Mock
    private OrderProjectionRepository orderProjectionRepository;
    @Mock
    private OrderProjectionMapper orderProjectionMapper;

    @Test
    void createsAProjectionFromAnHttpCreationEvent() {
        UUID alpacaOrderId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        OrderEvent event = event("HTTP", "create", connectionId.toString(), alpacaOrderId);
        when(orderProjectionRepository.findByAlpacaOrderId(alpacaOrderId)).thenReturn(Optional.empty());
        when(orderProjectionMapper.applyOrderEvent(any(OrderProjection.class), any(OrderEvent.class))).thenReturn(true);

        service().persist(event);

        ArgumentCaptor<OrderProjection> projection = ArgumentCaptor.forClass(OrderProjection.class);
        verify(orderProjectionRepository).save(projection.capture());
        org.junit.jupiter.api.Assertions.assertEquals(connectionId, projection.getValue().getConnectionId());
        org.junit.jupiter.api.Assertions.assertEquals(alpacaOrderId, projection.getValue().getAlpacaOrderId());
    }

    @Test
    void ignoresAnUnknownTradeUpdateWithoutAConnectionId() {
        UUID alpacaOrderId = UUID.randomUUID();
        OrderEvent event = event("trade_updates", "fill", null, alpacaOrderId);
        when(orderProjectionRepository.findByAlpacaOrderId(alpacaOrderId)).thenReturn(Optional.empty());

        service().persist(event);

        verify(orderProjectionRepository, never()).save(any());
        verify(orderProjectionMapper, never()).applyOrderEvent(any(), any());
    }

    private OrderEventPersistenceService service() {
        return new OrderEventPersistenceService(orderProjectionRepository, orderProjectionMapper);
    }

    private OrderEvent event(String stream, String type, String connectionId, UUID alpacaOrderId) {
        TradeUpdateOrder order = new TradeUpdateOrder();
        order.setId(alpacaOrderId.toString());
        OrderEvent event = new OrderEvent();
        event.setStream(stream);
        event.setEvent(type);
        event.setConnectionId(connectionId);
        event.setOrder(order);
        return event;
    }
}
