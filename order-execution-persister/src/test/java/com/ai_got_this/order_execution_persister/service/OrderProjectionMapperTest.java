package com.ai_got_this.order_execution_persister.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.dto.OrderEvent;
import com.ai_got_this.order_execution_persister.dto.TradeUpdateOrder;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderProjectionMapperTest {
    private final OrderProjectionMapper mapper = new OrderProjectionMapper();

    @Test
    void appliesTheLatestTradeUpdateToAnExistingProjection() {
        OrderProjection projection = new OrderProjection();
        projection.setUpdatedAt(Instant.parse("2026-08-19T00:59:00Z"));

        TradeUpdateOrder order = new TradeUpdateOrder();
        order.setSymbol("AAPL");
        order.setStatus("filled");
        order.setQty("2");
        order.setFilledQty("2");
        order.setFilledAvgPrice("180.18");
        order.setUpdatedAt("2026-08-19T01:00:00Z");
        order.setFilledAt("2026-08-19T01:00:00Z");
        OrderEvent event = new OrderEvent();
        event.setOrder(order);

        assertThat(mapper.applyOrderEvent(projection, event)).isTrue();
        assertThat(projection.getSymbol()).isEqualTo("AAPL");
        assertThat(projection.getStatus()).isEqualTo("filled");
        assertThat(projection.getFilledQuantity()).hasToString("2");
        assertThat(projection.getAverageFilledPrice()).hasToString("180.18");
        assertThat(projection.getUpdatedAt()).isEqualTo(Instant.parse("2026-08-19T01:00:00Z"));
    }
}
