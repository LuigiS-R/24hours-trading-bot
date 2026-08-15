package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountReconciliationService {
    final AlpacaTradingClient client;
    final AlpacaConnectionRepository connections;
    final TradingAccountProjectionRepository projections;

    public AccountReconciliationService(
            AlpacaTradingClient c, AlpacaConnectionRepository r, TradingAccountProjectionRepository p) {
        client = c;
        connections = r;
        projections = p;
    }

    @Transactional
    public TradingAccountProjection reconcileConnection(AlpacaConnection c) {
        try {
            AlpacaTradingClient.Account a = client.getAccount(c);
            if (a == null || a.id() == null) throw new IllegalStateException();
            c.setAlpacaAccountId(a.id());
            c.setStatus(AlpacaConnection.ConnectionStatus.CONNECTED);
            c.setLastVerifiedAt(Instant.now());
            c.setLastErrorCode(null);
            connections.save(c);
            TradingAccountProjection p =
                    projections.findById(c.getConnectionId()).orElseGet(TradingAccountProjection::new);
            p.setConnectionId(c.getConnectionId());
            p.setAlpacaAccountId(a.id());
            p.setAccountNumber(a.account_number());
            p.setStatus(a.status());
            p.setCurrency(a.currency());
            p.setCash(a.cash());
            p.setBuyingPower(a.buying_power());
            p.setNonMarginableBuyingPower(a.non_marginable_buying_power());
            p.setPortfolioValue(a.portfolio_value());
            p.setEquity(a.equity());
            p.setLastEquity(a.last_equity());
            p.setLongMarketValue(a.long_market_value());
            p.setAccountBlocked(a.account_blocked());
            p.setTradingBlocked(a.trading_blocked());
            p.setTradeSuspendedByUser(a.trade_suspended_by_user());
            p.setShortingEnabled(a.shorting_enabled());
            p.setLastSyncedAt(Instant.now());
            return projections.save(p);
        } catch (ApiException e) {
            markConnectionFailure(c, e.code);
            throw e;
        } catch (Exception e) {
            markConnectionFailure(c, "ALPACA_ACCOUNT_VERIFICATION_FAILED");
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "ALPACA_ACCOUNT_VERIFICATION_FAILED",
                    "Unable to verify Alpaca account.");
        }
    }

    @Transactional
    public void markConnectionFailure(AlpacaConnection c, String code) {
        c.setLastErrorCode(code);
        if ("ALPACA_REAUTH_REQUIRED".equals(code))
            c.setStatus(AlpacaConnection.ConnectionStatus.REAUTH_REQUIRED);
        else c.setStatus(AlpacaConnection.ConnectionStatus.ERROR);
        connections.save(c);
    }
}
