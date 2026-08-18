package com.ai_got_this.order_execution_service.dto;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderContractTest {

    @Test
    void acceptsTheStrategyDecisionLimitOrderShape() throws Exception {
        Order order = new ObjectMapper().readValue("""
                {
                  "ticker":"AAPL",
                  "action":"BUY",
                  "quantity":2,
                  "order_type":"LIMIT",
                  "limit_price":180.18
                }
                """, Order.class);

        assertEquals("AAPL", order.getSymbol());
        assertEquals("BUY", order.getSide());
        assertEquals(2, order.getQuantity());
        assertEquals("LIMIT", order.getType());
        assertEquals(new BigDecimal("180.18"), order.getLimitPrice());
    }
}
