package com.ai_got_this.order_execution_persister.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalOrderProjectionControllerTest {

    private final OrderProjectionRepository repository = mock(OrderProjectionRepository.class);
    private final InternalOrderProjectionController controller = new InternalOrderProjectionController(repository);

    @Test
    void returnsTheLatestProjectionRowsForTheRequestedConnection() {
        UUID connectionId = UUID.randomUUID();
        OrderProjection projection = new OrderProjection();
        projection.setOrderId(UUID.randomUUID());
        projection.setConnectionId(connectionId);
        projection.setAlpacaOrderId(UUID.randomUUID());
        projection.setSymbol("AAPL");
        projection.setQuantity(new BigDecimal("2"));
        projection.setStatus("filled");
        projection.setUpdatedAt(Instant.parse("2026-08-22T00:00:00Z"));
        when(repository.findTop20ByConnectionIdOrderByUpdatedAtDesc(connectionId)).thenReturn(List.of(projection));

        List<InternalOrderProjectionController.OrderProjectionResponse> result = controller.latestOrders(connectionId);

        assertThat(result).singleElement().satisfies(order -> {
            assertThat(order.orderId()).isEqualTo(projection.getOrderId());
            assertThat(order.connectionId()).isEqualTo(connectionId);
            assertThat(order.alpacaOrderId()).isEqualTo(projection.getAlpacaOrderId());
            assertThat(order.symbol()).isEqualTo("AAPL");
            assertThat(order.quantity()).isEqualByComparingTo("2");
            assertThat(order.status()).isEqualTo("filled");
        });
        verify(repository).findTop20ByConnectionIdOrderByUpdatedAtDesc(connectionId);
    }
}
