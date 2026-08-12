package com.ai_got_this.accounts_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetAccountResponse{
    private String accountNumber;
    private double cash;
    private String createdAt;
    private String currency;
}
