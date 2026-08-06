package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    private String event;
    private String cancelRequestedAt;
    private String canceledAt;
    private String clientOrderId;
    private String updatedAt;

    public OrderEvent(TradeUpdateResponse tradeUpdateResponse) {
        TradeUpdateData data = tradeUpdateResponse.getData();
        if (data != null) {
            event = data.getEvent();

            TradeUpdateOrder order = data.getOrder();
            if (order != null) {
                cancelRequestedAt = order.getCancelRequestedAt();
                canceledAt = order.getCanceledAt();
                clientOrderId = order.getClientOrderId();
                updatedAt = order.getUpdatedAt();
            }
        }
    }
}
