package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class OrderResponse {
    //"client_order_id": "5b5d3d67-06ad-4ffa-af65-a117d0fc5a59",
    private String clientOrderId;
}
