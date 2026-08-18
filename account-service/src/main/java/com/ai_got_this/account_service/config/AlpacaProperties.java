package com.ai_got_this.account_service.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca")
public record AlpacaProperties(
        OAuth oauth, String paperTradingBaseUrl, Duration reconciliationInterval) {

        public record OAuth(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizeUrl,
            String tokenUrl,
            long stateTtl) {}
}
