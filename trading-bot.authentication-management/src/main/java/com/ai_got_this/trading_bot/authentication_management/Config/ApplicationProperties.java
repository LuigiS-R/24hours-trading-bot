package com.ai_got_this.trading_bot.authentication_management.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "authentication-management")
public class ApplicationProperties {
    private String jwtSecret;
    private Long jwtExpiration;
}
