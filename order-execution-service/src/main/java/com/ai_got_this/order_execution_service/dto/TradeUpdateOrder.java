package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeUpdateOrder {
    private String cancelRequestedAt;
    private String canceledAt;
    private String clientOrderId;
    private String updatedAt;
}