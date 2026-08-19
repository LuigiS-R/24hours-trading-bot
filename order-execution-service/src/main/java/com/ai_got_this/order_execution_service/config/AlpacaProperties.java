package com.ai_got_this.order_execution_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("alpaca.api")
@Validated
@Data
public class AlpacaProperties {
    @NotBlank
    private String key;
    @NotBlank
    private String secret;
    @NotBlank
    private String streamUrl;
    @NotBlank
    private String subscriptionMessage;
    @NotBlank
    private String orderEndpoint;
    @NotBlank
    private String paperTradingBaseUrl;
}
