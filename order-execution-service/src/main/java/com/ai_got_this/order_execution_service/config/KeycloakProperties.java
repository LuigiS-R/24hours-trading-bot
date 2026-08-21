package com.ai_got_this.order_execution_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("keycloak")
@Data
public class KeycloakProperties {
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
}
