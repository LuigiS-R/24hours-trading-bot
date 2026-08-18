package com.ai_got_this.order_execution_service.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderEventTest {

    @Test
    void flattensTradeUpdateResponse() throws Exception {
        TradeUpdateOrder order = new TradeUpdateOrder();
        order.setCancelRequestedAt(null);
        order.setCanceledAt(null);
        order.setClientOrderId("4642fd68-d59a-47d7-a9ac-e22f536828d1");
        order.setUpdatedAt("2022-04-19T13:45:05.027690731-04:00");

        TradeUpdateData data = new TradeUpdateData();
        data.setEvent("fill");
        data.setOrder(order);

        TradeUpdateResponse response = new TradeUpdateResponse();
        response.setData(data);

        OrderEvent orderEvent = new OrderEvent(response);
        ObjectMapper objectMapper = new ObjectMapper();
        var jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(orderEvent));

        assertEquals("fill", orderEvent.getEvent());
        assertEquals("4642fd68-d59a-47d7-a9ac-e22f536828d1", orderEvent.getClientOrderId());
        assertEquals("2022-04-19T13:45:05.027690731-04:00", orderEvent.getUpdatedAt());

        assertTrue(jsonNode.has("event"));
        assertTrue(jsonNode.has("cancelRequestedAt"));
        assertTrue(jsonNode.has("canceledAt"));
        assertTrue(jsonNode.has("clientOrderId"));
        assertTrue(jsonNode.has("updatedAt"));
        assertFalse(jsonNode.has("stream"));
        assertFalse(jsonNode.has("cancel_requested_at"));
        assertFalse(jsonNode.has("canceled_at"));
        assertFalse(jsonNode.has("client_order_id"));
        assertFalse(jsonNode.has("updated_at"));
        assertFalse(jsonNode.has("data"));
        assertFalse(jsonNode.has("order"));
    }
}
