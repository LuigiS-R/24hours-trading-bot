package com.ai_got_this.order_execution_service.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderEventTest {

    @Test
    void preservesTheRelevantFieldsFromAnAlpacaTradeUpdate() throws Exception {
        TradeUpdateResponse response = new ObjectMapper().readValue("""
                {
                  "stream": "trade_updates",
                  "data": {
                    "event": "fill",
                    "event_id": "event-1",
                    "execution_id": "execution-1",
                    "timestamp": "2026-08-19T01:00:00Z",
                    "price": "180.18",
                    "qty": "2",
                    "position_qty": "2",
                    "order": {
                      "id": "order-1",
                      "client_order_id": "client-order-1",
                      "status": "filled",
                      "filled_qty": "2",
                      "filled_avg_price": "180.18",
                      "updated_at": "2026-08-19T01:00:00Z"
                    }
                  }
                }
                """, TradeUpdateResponse.class);

        OrderEvent event = new OrderEvent(response);

        assertEquals("event-1", event.getEventId());
        assertEquals("execution-1", event.getExecutionId());
        assertEquals("2", event.getPositionQty());
        assertEquals(null, event.getConnectionId());
        assertEquals("client-order-1", event.getOrder().getClientOrderId());
        assertEquals("filled", event.getOrder().getStatus());
        assertEquals("2", event.getOrder().getFilledQty());
    }

    @Test
    void mapsTradeUpdateResponseToThePublishedEventShape() throws Exception {
        TradeUpdateOrder order = new TradeUpdateOrder();
        order.setId("a5be8f5e-fdfa-41f5-a644-7a74fe947a8f");
        order.setClientOrderId("4642fd68-d59a-47d7-a9ac-e22f536828d1");
        order.setUpdatedAt("2022-04-19T13:45:05.027690731-04:00");
        order.setFilledQty("2");
        order.setFilledAvgPrice("180.18");

        TradeUpdateData data = new TradeUpdateData();
        data.setEvent("fill");
        data.setExecutionId("2f63ea93-423d-4169-b3f6-3fdafc10c418");
        data.setTimestamp("2022-04-19T17:45:05.024916716Z");
        data.setPrice("180.18");
        data.setQty("2");
        data.setPositionQty("2");
        data.setOrder(order);

        TradeUpdateResponse response = new TradeUpdateResponse();
        response.setStream("trade_updates");
        response.setData(data);

        OrderEvent orderEvent = new OrderEvent(response);
        ObjectMapper objectMapper = new ObjectMapper();
        var jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(orderEvent));

        assertEquals("fill", orderEvent.getEvent());
        assertEquals("trade_updates", orderEvent.getStream());
        assertEquals("2f63ea93-423d-4169-b3f6-3fdafc10c418", orderEvent.getExecutionId());
        assertEquals("180.18", orderEvent.getPrice());
        assertEquals("4642fd68-d59a-47d7-a9ac-e22f536828d1", orderEvent.getOrder().getClientOrderId());
        assertEquals("2022-04-19T13:45:05.027690731-04:00", orderEvent.getOrder().getUpdatedAt());

        assertTrue(jsonNode.has("event"));
        assertTrue(jsonNode.has("connectionId"));
        assertTrue(jsonNode.has("stream"));
        assertTrue(jsonNode.has("executionId"));
        assertTrue(jsonNode.has("timestamp"));
        assertTrue(jsonNode.has("price"));
        assertTrue(jsonNode.has("qty"));
        assertTrue(jsonNode.has("positionQty"));
        assertTrue(jsonNode.has("order"));
        assertTrue(jsonNode.path("order").has("clientOrderId"));
        assertTrue(jsonNode.path("order").has("filledQty"));
    }

    @Test
    void createsAnHttpCreationEventWithTheSubmittingConnection() {
        TradeUpdateOrder order = new TradeUpdateOrder();
        order.setId("a5be8f5e-fdfa-41f5-a644-7a74fe947a8f");

        OrderEvent event = OrderEvent.httpOrderCreated("cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64", order);

        assertEquals("HTTP", event.getStream());
        assertEquals("create", event.getEvent());
        assertEquals("cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64", event.getConnectionId());
        assertEquals(order, event.getOrder());
    }
}
