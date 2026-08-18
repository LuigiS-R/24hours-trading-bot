package com.ai_got_this.account_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.OrderProjection;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.OrderProjectionRepository;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccountReconciliationServiceTest {

    @Mock
    private AlpacaTradingClient alpacaTradingClient;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private TradingAccountProjectionRepository tradingAccountProjectionRepository;

    @Mock
    private PositionProjectionRepository positionProjectionRepository;

    @Mock
    private OrderProjectionRepository orderProjectionRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private WatchlistEntryRepository watchlistEntryRepository;

    @Mock
    private ConnectionFailureService connectionFailureService;

    @Test
    void reconcileConnectionUpdatesProjectionAndImportsBrokerWatchlists() {
        AlpacaConnection connection = connection();
        UUID brokerWatchlistId = UUID.randomUUID();
        when(alpacaTradingClient.getAccount(connection)).thenReturn(account(connection.getAlpacaAccountId()));
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.empty());
        when(tradingAccountProjectionRepository.save(any(TradingAccountProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alpacaTradingClient.getPositions(connection)).thenReturn(List.of());
        when(alpacaTradingClient.getOrders(connection)).thenReturn(List.of());
        when(positionProjectionRepository.findByConnectionId(connection.getConnectionId())).thenReturn(List.of());
        when(alpacaTradingClient.getWatchlists(connection)).thenReturn(List.of(
                new AlpacaTradingClient.BrokerWatchlist(
                        brokerWatchlistId,
                        "Technology",
                        List.of(new AlpacaTradingClient.BrokerAsset("AAPL")))));
        when(watchlistRepository.findByConnectionId(connection.getConnectionId())).thenReturn(List.of());
        when(watchlistRepository.save(any(Watchlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(watchlistEntryRepository.findByWatchlistId(any())).thenReturn(List.of());
        when(watchlistEntryRepository.save(any(WatchlistEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TradingAccountProjection projection = service().reconcileConnection(connection);

        assertThat(projection.getConnectionId()).isEqualTo(connection.getConnectionId());
        assertThat(projection.getCash()).isEqualByComparingTo("1000.25");
        assertThat(projection.getLastSyncedAt()).isNotNull();
        assertThat(connection.getStatus()).isEqualTo(AlpacaConnection.ConnectionStatus.CONNECTED);
        ArgumentCaptor<Watchlist> watchlistCaptor = ArgumentCaptor.forClass(Watchlist.class);
        verify(watchlistRepository).save(watchlistCaptor.capture());
        assertThat(watchlistCaptor.getValue().getAlpacaWatchlistId()).isEqualTo(brokerWatchlistId);
        assertThat(watchlistCaptor.getValue().getSyncStatus()).isEqualTo(Watchlist.SyncStatus.SYNCED);
        ArgumentCaptor<WatchlistEntry> entryCaptor = ArgumentCaptor.forClass(WatchlistEntry.class);
        verify(watchlistEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getTicker()).isEqualTo("AAPL");
    }

    @Test
    void reconcileConnectionRemovesWatchlistsDeletedAtBroker() {
        AlpacaConnection connection = connection();
        Watchlist localWatchlist = new Watchlist();
        localWatchlist.setWatchlistId(UUID.randomUUID());
        localWatchlist.setConnectionId(connection.getConnectionId());
        localWatchlist.setAlpacaWatchlistId(UUID.randomUUID());
        WatchlistEntry localEntry = new WatchlistEntry();
        localEntry.setEntryId(UUID.randomUUID());
        localEntry.setWatchlistId(localWatchlist.getWatchlistId());
        localEntry.setTicker("AAPL");
        when(alpacaTradingClient.getAccount(connection)).thenReturn(account(connection.getAlpacaAccountId()));
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(new TradingAccountProjection()));
        when(tradingAccountProjectionRepository.save(any(TradingAccountProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alpacaTradingClient.getPositions(connection)).thenReturn(List.of());
        when(alpacaTradingClient.getOrders(connection)).thenReturn(List.of());
        when(positionProjectionRepository.findByConnectionId(connection.getConnectionId())).thenReturn(List.of());
        when(alpacaTradingClient.getWatchlists(connection)).thenReturn(List.of());
        when(watchlistRepository.findByConnectionId(connection.getConnectionId()))
                .thenReturn(List.of(localWatchlist));
        when(watchlistEntryRepository.findByWatchlistId(localWatchlist.getWatchlistId()))
                .thenReturn(List.of(localEntry));

        service().reconcileConnection(connection);

        verify(watchlistEntryRepository).deleteAll(List.of(localEntry));
        verify(watchlistRepository).delete(localWatchlist);
        verify(watchlistRepository).flush();
    }

    @Test
    void reconcileConnectionReplacesPositionsAndUpsertsOrders() {
        AlpacaConnection connection = connection();
        PositionProjection removedPosition = new PositionProjection();
        removedPosition.setPositionId(UUID.randomUUID());
        removedPosition.setConnectionId(connection.getConnectionId());
        removedPosition.setSymbol("MSFT");
        removedPosition.setQuantity(BigDecimal.ONE);
        removedPosition.setUpdatedAt(java.time.Instant.now());
        UUID assetId = UUID.randomUUID();
        UUID brokerOrderId = UUID.randomUUID();

        when(alpacaTradingClient.getAccount(connection)).thenReturn(account(connection.getAlpacaAccountId()));
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.empty());
        when(tradingAccountProjectionRepository.save(any(TradingAccountProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alpacaTradingClient.getPositions(connection)).thenReturn(List.of(new AlpacaTradingClient.BrokerPosition(
                assetId, "AAPL", new BigDecimal("2"), new BigDecimal("1.5"), "long",
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("110"),
                new BigDecimal("220"), new BigDecimal("20"))));
        when(positionProjectionRepository.findByConnectionId(connection.getConnectionId()))
                .thenReturn(List.of(removedPosition));
        when(positionProjectionRepository.save(any(PositionProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alpacaTradingClient.getOrders(connection)).thenReturn(List.of(new AlpacaTradingClient.BrokerOrder(
                brokerOrderId, "client-1", "AAPL", "buy", "market", "day", "filled",
                new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("101"), new BigDecimal("0.05"),
                java.time.Instant.parse("2026-08-18T00:00:00Z"),
                java.time.Instant.parse("2026-08-18T00:01:00Z"),
                java.time.Instant.parse("2026-08-18T00:01:00Z"), null)));
        when(orderProjectionRepository.findByAlpacaOrderId(brokerOrderId)).thenReturn(Optional.empty());
        when(orderProjectionRepository.save(any(OrderProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alpacaTradingClient.getWatchlists(connection)).thenReturn(List.of());
        when(watchlistRepository.findByConnectionId(connection.getConnectionId())).thenReturn(List.of());

        service().reconcileConnection(connection);

        ArgumentCaptor<PositionProjection> positionCaptor = ArgumentCaptor.forClass(PositionProjection.class);
        verify(positionProjectionRepository).save(positionCaptor.capture());
        assertThat(positionCaptor.getValue().getSymbol()).isEqualTo("AAPL");
        assertThat(positionCaptor.getValue().getAlpacaAssetId()).isEqualTo(assetId);
        assertThat(positionCaptor.getValue().getUnrealizedPnl()).isEqualByComparingTo("20");
        verify(positionProjectionRepository).delete(removedPosition);

        ArgumentCaptor<OrderProjection> orderCaptor = ArgumentCaptor.forClass(OrderProjection.class);
        verify(orderProjectionRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getAlpacaOrderId()).isEqualTo(brokerOrderId);
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo("filled");
        assertThat(orderCaptor.getValue().getFees()).isEqualByComparingTo("0.05");
    }

    @Test
    void reconcileConnectionMarksConnectionForReauthenticationWhenSecretIsMissing() {
        AlpacaConnection connection = connection();
        ApiException brokerFailure = new ApiException(
                HttpStatus.CONFLICT,
                "ALPACA_REAUTH_REQUIRED",
                "Reconnect the account.");
        when(alpacaTradingClient.getAccount(connection)).thenThrow(brokerFailure);

        assertThatThrownBy(() -> service().reconcileConnection(connection))
                .isSameAs(brokerFailure);

        verify(connectionFailureService).markConnectionFailure(
                connection.getConnectionId(),
                "ALPACA_REAUTH_REQUIRED");
    }

    @Test
    void reconcileConnectionMarksGenericFailuresWithoutLeakingImplementationErrors() {
        AlpacaConnection connection = connection();
        when(alpacaTradingClient.getAccount(connection)).thenThrow(new IllegalStateException("network"));

        assertThatThrownBy(() -> service().reconcileConnection(connection))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("ALPACA_ACCOUNT_VERIFICATION_FAILED");
                });

        verify(connectionFailureService).markConnectionFailure(
                connection.getConnectionId(),
                "ALPACA_ACCOUNT_VERIFICATION_FAILED");
    }

    private AccountReconciliationService service() {
        return new AccountReconciliationService(
                alpacaTradingClient,
                alpacaConnectionRepository,
                tradingAccountProjectionRepository,
                positionProjectionRepository,
                orderProjectionRepository,
                watchlistRepository,
                watchlistEntryRepository,
                connectionFailureService);
    }

    private AlpacaConnection connection() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        connection.setAlpacaAccountId(UUID.randomUUID());
        connection.setStatus(AlpacaConnection.ConnectionStatus.PENDING);
        return connection;
    }

    private AlpacaTradingClient.Account account(UUID accountId) {
        return new AlpacaTradingClient.Account(
                accountId,
                "PA12345678",
                "ACTIVE",
                "USD",
                new BigDecimal("1000.25"),
                new BigDecimal("2000.50"),
                new BigDecimal("1000.25"),
                new BigDecimal("1100.75"),
                new BigDecimal("1100.75"),
                new BigDecimal("1000.00"),
                new BigDecimal("1100.75"),
                false,
                false,
                false,
                true);
    }
}
