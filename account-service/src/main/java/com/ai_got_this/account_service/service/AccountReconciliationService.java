package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountReconciliationService {

    private final AlpacaTradingClient alpacaTradingClient;
    private final AlpacaConnectionRepository alpacaConnectionRepository;
    private final TradingAccountProjectionRepository tradingAccountProjectionRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistEntryRepository watchlistEntryRepository;
    private final ConnectionFailureService connectionFailureService;

    public AccountReconciliationService(
            AlpacaTradingClient alpacaTradingClient,
            AlpacaConnectionRepository alpacaConnectionRepository,
            TradingAccountProjectionRepository tradingAccountProjectionRepository,
            WatchlistRepository watchlistRepository,
            WatchlistEntryRepository watchlistEntryRepository,
            ConnectionFailureService connectionFailureService) {
        this.alpacaTradingClient = alpacaTradingClient;
        this.alpacaConnectionRepository = alpacaConnectionRepository;
        this.tradingAccountProjectionRepository = tradingAccountProjectionRepository;
        this.watchlistRepository = watchlistRepository;
        this.watchlistEntryRepository = watchlistEntryRepository;
        this.connectionFailureService = connectionFailureService;
    }

    @Transactional
    public TradingAccountProjection reconcileConnection(AlpacaConnection connection) {
        try {
            AlpacaTradingClient.Account account = alpacaTradingClient.getAccount(connection);
            if (account == null || account.id() == null) {
                throw new IllegalStateException("Alpaca did not return an account identifier.");
            }

            updateConnection(connection, account);
            TradingAccountProjection projection = updateAccountProjection(connection, account);
            reconcileWatchlists(connection);

            return projection;
        } catch (ApiException exception) {
            connectionFailureService.markConnectionFailure(
                    connection.getConnectionId(),
                    exception.code);
            throw exception;
        } catch (Exception exception) {
            connectionFailureService.markConnectionFailure(
                    connection.getConnectionId(),
                    "ALPACA_ACCOUNT_VERIFICATION_FAILED");
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ALPACA_ACCOUNT_VERIFICATION_FAILED",
                    "Unable to reconcile the Alpaca account.");
        }
    }

    private void updateConnection(
            AlpacaConnection connection,
            AlpacaTradingClient.Account account) {
        connection.setAlpacaAccountId(account.id());
        connection.setStatus(AlpacaConnection.ConnectionStatus.CONNECTED);
        connection.setLastVerifiedAt(Instant.now());
        connection.setLastErrorCode(null);
        alpacaConnectionRepository.save(connection);
    }

    private TradingAccountProjection updateAccountProjection(
            AlpacaConnection connection,
            AlpacaTradingClient.Account account) {
        TradingAccountProjection projection = tradingAccountProjectionRepository
                .findById(connection.getConnectionId())
                .orElseGet(TradingAccountProjection::new);

        projection.setConnectionId(connection.getConnectionId());
        projection.setAlpacaAccountId(account.id());
        projection.setAccountNumber(account.account_number());
        projection.setStatus(account.status());
        projection.setCurrency(account.currency());
        projection.setCash(account.cash());
        projection.setBuyingPower(account.buying_power());
        projection.setNonMarginableBuyingPower(account.non_marginable_buying_power());
        projection.setPortfolioValue(account.portfolio_value());
        projection.setEquity(account.equity());
        projection.setLastEquity(account.last_equity());
        projection.setLongMarketValue(account.long_market_value());
        projection.setAccountBlocked(account.account_blocked());
        projection.setTradingBlocked(account.trading_blocked());
        projection.setTradeSuspendedByUser(account.trade_suspended_by_user());
        projection.setShortingEnabled(account.shorting_enabled());
        projection.setLastSyncedAt(Instant.now());

        return tradingAccountProjectionRepository.save(projection);
    }

    private void reconcileWatchlists(AlpacaConnection connection) {
        List<AlpacaTradingClient.BrokerWatchlist> brokerWatchlists =
                alpacaTradingClient.getWatchlists(connection);
        List<Watchlist> localWatchlists = watchlistRepository.findByConnectionId(
                connection.getConnectionId());
        Set<UUID> brokerWatchlistIds = brokerWatchlists.stream()
                .map(AlpacaTradingClient.BrokerWatchlist::id)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        deleteWatchlistsRemovedAtBroker(localWatchlists, brokerWatchlistIds);

        Map<UUID, Watchlist> localWatchlistsByBrokerId = new HashMap<>();
        for (Watchlist localWatchlist : localWatchlists) {
            if (brokerWatchlistIds.contains(localWatchlist.getAlpacaWatchlistId())) {
                localWatchlistsByBrokerId.put(
                        localWatchlist.getAlpacaWatchlistId(),
                        localWatchlist);
            }
        }

        for (AlpacaTradingClient.BrokerWatchlist brokerWatchlist : brokerWatchlists) {
            if (brokerWatchlist.id() == null || brokerWatchlist.name() == null) {
                continue;
            }

            Watchlist localWatchlist = localWatchlistsByBrokerId.get(brokerWatchlist.id());
            if (localWatchlist == null) {
                localWatchlist = new Watchlist();
                localWatchlist.setWatchlistId(UUID.randomUUID());
                localWatchlist.setConnectionId(connection.getConnectionId());
                localWatchlist.setAlpacaWatchlistId(brokerWatchlist.id());
            }

            localWatchlist.setName(brokerWatchlist.name());
            localWatchlist.setSyncStatus(Watchlist.SyncStatus.SYNCED);
            localWatchlist.setLastSyncErrorCode(null);
            watchlistRepository.save(localWatchlist);
            reconcileWatchlistEntries(localWatchlist, brokerWatchlist.assets());
        }
    }

    private void deleteWatchlistsRemovedAtBroker(
            List<Watchlist> localWatchlists,
            Set<UUID> brokerWatchlistIds) {
        for (Watchlist localWatchlist : localWatchlists) {
            if (!brokerWatchlistIds.contains(localWatchlist.getAlpacaWatchlistId())) {
                watchlistEntryRepository.deleteAll(
                        watchlistEntryRepository.findByWatchlistId(localWatchlist.getWatchlistId()));
                watchlistRepository.delete(localWatchlist);
            }
        }
        watchlistRepository.flush();
    }

    private void reconcileWatchlistEntries(
            Watchlist watchlist,
            List<AlpacaTradingClient.BrokerAsset> brokerAssets) {
        Map<String, WatchlistEntry> localEntriesByTicker = new HashMap<>();
        for (WatchlistEntry localEntry : watchlistEntryRepository.findByWatchlistId(
                watchlist.getWatchlistId())) {
            localEntriesByTicker.put(localEntry.getTicker(), localEntry);
        }

        Set<String> brokerTickers = new HashSet<>();
        for (AlpacaTradingClient.BrokerAsset brokerAsset : brokerAssets == null
                ? List.<AlpacaTradingClient.BrokerAsset>of()
                : brokerAssets) {
            if (brokerAsset.symbol() == null) {
                continue;
            }

            brokerTickers.add(brokerAsset.symbol());
            if (!localEntriesByTicker.containsKey(brokerAsset.symbol())) {
                WatchlistEntry localEntry = new WatchlistEntry();
                localEntry.setEntryId(UUID.randomUUID());
                localEntry.setWatchlistId(watchlist.getWatchlistId());
                localEntry.setTicker(brokerAsset.symbol());
                watchlistEntryRepository.save(localEntry);
            }
        }

        for (WatchlistEntry localEntry : localEntriesByTicker.values()) {
            if (!brokerTickers.contains(localEntry.getTicker())) {
                watchlistEntryRepository.delete(localEntry);
            }
        }
    }
}
