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
                  "connectionId":"cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64",
                  "ticker":"AAPL",
                  "action":"BUY",
                  "quantity":2,
                  "order_type":"LIMIT",
                  "limit_price":180.18,
                  "reason":"LSTM UP prediction with 80.0% confidence"
                }
                """, Order.class);

        assertEquals("cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64", order.getConnectionId());
        assertEquals("AAPL", order.getSymbol());
        assertEquals("BUY", order.getSide());
        assertEquals(2, order.getQuantity());
        assertEquals("LIMIT", order.getType());
        assertEquals(new BigDecimal("180.18"), order.getLimitPrice());
        assertEquals("LSTM UP prediction with 80.0% confidence", order.getReason());
    }
}
