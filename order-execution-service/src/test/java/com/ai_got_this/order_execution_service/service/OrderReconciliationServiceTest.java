package com.ai_got_this.order_execution_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import com.ai_got_this.order_execution_service.client.AlpacaOrdersClient;
import com.ai_got_this.order_execution_service.domain.OrderProjection;
import com.ai_got_this.order_execution_service.repository.OrderProjectionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderReconciliationServiceTest {
    @Mock
    private AccountServiceClient accountServiceClient;
    @Mock
    private AlpacaOrdersClient alpacaOrdersClient;
    @Mock
    private OrderProjectionRepository orderProjectionRepository;

    @Test
    void upsertsByAlpacaOrderIdAndUsesTheBrokerUpdatedTimestamp() {
        UUID connectionId = UUID.randomUUID();
        UUID alpacaOrderId = UUID.randomUUID();
        OrderProjection existing = new OrderProjection();
        UUID localOrderId = UUID.randomUUID();
        existing.setOrderId(localOrderId);
        existing.setAlpacaOrderId(alpacaOrderId);
        Instant updatedAt = Instant.parse("2026-08-18T00:01:00Z");
        when(accountServiceClient.brokerCredential(connectionId))
                .thenReturn(new AccountServiceClient.BrokerCredential(connectionId, "oauth-token"));
        when(alpacaOrdersClient.getOrders("oauth-token")).thenReturn(List.of(new AlpacaOrdersClient.BrokerOrder(
                alpacaOrderId, "client-1", "AAPL", "buy", "market", "day", "filled",
                new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("101"), new BigDecimal("0.05"),
                Instant.parse("2026-08-18T00:00:00Z"), updatedAt, updatedAt, null)));
        when(orderProjectionRepository.findByAlpacaOrderId(alpacaOrderId)).thenReturn(Optional.of(existing));
        when(orderProjectionRepository.save(any(OrderProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().reconcileOrders(connectionId);

        ArgumentCaptor<OrderProjection> captor = ArgumentCaptor.forClass(OrderProjection.class);
        verify(orderProjectionRepository).save(captor.capture());
        OrderProjection saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(localOrderId);
        assertThat(saved.getConnectionId()).isEqualTo(connectionId);
        assertThat(saved.getAlpacaOrderId()).isEqualTo(alpacaOrderId);
        assertThat(saved.getStatus()).isEqualTo("filled");
        assertThat(saved.getFees()).isEqualByComparingTo("0.05");
        assertThat(saved.getUpdatedAt()).isEqualTo(updatedAt);
    }

    private OrderReconciliationService service() {
        return new OrderReconciliationService(accountServiceClient, alpacaOrdersClient, orderProjectionRepository);
    }
}
