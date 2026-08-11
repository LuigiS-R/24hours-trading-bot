package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Order {
    private String symbol;
    @JsonProperty("qty")
    private int quantity;
    private String side;
    private String type = "market";
    private String timeInForce;
}