package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountReconciliationService {
    private final AlpacaTradingClient broker;
    private final TradingAccountProjectionRepository accounts;
    private final PositionProjectionRepository positions;

    public AccountReconciliationService(AlpacaTradingClient broker, TradingAccountProjectionRepository accounts,
                                        PositionProjectionRepository positions) {
        this.broker = broker; this.accounts = accounts; this.positions = positions;
    }

    @Transactional
    public TradingAccountProjection reconcile() {
        AlpacaTradingClient.BrokerAccount source = broker.account();
        if (source == null || source.id() == null) throw new IllegalStateException("Alpaca did not return an account.");
        TradingAccountProjection target = accounts.findAll().stream().findFirst().orElseGet(TradingAccountProjection::new);
        target.setProjectionId(target.getProjectionId() == null ? UUID.randomUUID() : target.getProjectionId());
        target.setAlpacaAccountId(source.id()); target.setAccountNumber(source.account_number()); target.setStatus(source.status());
        target.setCurrency(source.currency()); target.setCash(source.cash()); target.setBuyingPower(source.buying_power());
        target.setNonMarginableBuyingPower(source.non_marginable_buying_power()); target.setPortfolioValue(source.portfolio_value());
        target.setEquity(source.equity()); target.setLastEquity(source.last_equity()); target.setLongMarketValue(source.long_market_value());
        target.setAccountBlocked(source.account_blocked()); target.setTradingBlocked(source.trading_blocked());
        target.setTradeSuspendedByUser(source.trade_suspended_by_user()); target.setShortingEnabled(source.shorting_enabled());
        target.setLastSyncedAt(Instant.now()); accounts.save(target);

        positions.deleteAll();
        for (AlpacaTradingClient.BrokerPosition position : broker.positions()) {
            PositionProjection projection = new PositionProjection(); projection.setPositionId(UUID.randomUUID());
            projection.setAlpacaAssetId(position.asset_id()); projection.setSymbol(position.symbol()); projection.setQuantity(position.qty());
            projection.setAverageEntryPrice(position.avg_entry_price()); projection.setMarketValue(position.market_value());
            projection.setCostBasis(position.cost_basis()); projection.setCurrentPrice(position.current_price()); projection.setUnrealizedPnl(position.unrealized_pl());
            projection.setSide(position.side()); projection.setUpdatedAt(Instant.now()); positions.save(projection);
        }
        return target;
    }
}
