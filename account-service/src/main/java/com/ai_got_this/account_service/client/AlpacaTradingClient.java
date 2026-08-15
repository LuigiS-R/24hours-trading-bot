package com.ai_got_this.account_service.client;

import com.ai_got_this.account_service.config.AlpacaProperties;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AlpacaTradingClient {
    final WebClient web;
    final SecretStore secrets;

    public AlpacaTradingClient(AlpacaProperties p, SecretStore s) {
        web = WebClient.builder().baseUrl(p.paperTradingBaseUrl()).build();
        secrets = s;
    }

    private WebClient.RequestHeadersSpec<?> request(HttpMethod m, String uri, AlpacaConnection c) {
        String t = secrets.read(c.getSecretReference());
        if (t == null)
            throw new ApiException(HttpStatus.CONFLICT, "ALPACA_REAUTH_REQUIRED", "Reconnect your Alpaca paper account to continue.");

        return web.method(m).uri(uri).headers(h -> h.setBearerAuth(t));
    }

    public Account getAccount(AlpacaConnection c) {
        try {
            return request(HttpMethod.GET, "/v2/account", c).retrieve().bodyToMono(Account.class).block();
        }
        catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ALPACA_ACCOUNT_VERIFICATION_FAILED", "Unable to verify Alpaca account.");
        }
    }

    public record Account(
            UUID id,
            String account_number,
            String status,
            String currency,
            BigDecimal cash,
            BigDecimal buying_power,
            BigDecimal non_marginable_buying_power,
            BigDecimal portfolio_value,
            BigDecimal equity,
            BigDecimal last_equity,
            BigDecimal long_market_value,
            boolean account_blocked,
            boolean trading_blocked,
            boolean trade_suspended_by_user,
            boolean shorting_enabled) {}
}
