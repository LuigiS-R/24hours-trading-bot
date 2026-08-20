package com.ai_got_this.order_execution_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties("account-service.internal")
@Validated
@Data
public class AccountServiceProperties {
    @NotBlank
    private String baseUrl;
    @NotBlank
    private String serviceToken;
}
