package com.ai_got_this.account_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import com.ai_got_this.account_service.service.ApiException;
import com.ai_got_this.account_service.service.CurrentUserService;
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
class WatchlistControllerTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private WatchlistEntryRepository watchlistEntryRepository;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private AlpacaTradingClient alpacaTradingClient;

    @Test
    void createCallsAlpacaBeforePersistingTheSyncedLocalWatchlist() {
        AlpacaConnection connection = connection();
        UUID brokerWatchlistId = UUID.randomUUID();
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(watchlistRepository.findByConnectionIdAndName(connection.getConnectionId(), "Technology"))
                .thenReturn(Optional.empty());
        when(alpacaTradingClient.createWatchlist(connection, "Technology", List.of("AAPL", "MSFT")))
                .thenReturn(new AlpacaTradingClient.BrokerWatchlist(brokerWatchlistId, "Technology", List.of()));
        when(watchlistRepository.save(any(Watchlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(watchlistEntryRepository.save(any(WatchlistEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WatchlistController.View view = controller().create(
                connection.getConnectionId(),
                UUID.randomUUID(),
                new WatchlistController.Write(" Technology ", List.of(" aapl ", "msft")));

        assertThat(view.watchlist().getAlpacaWatchlistId()).isEqualTo(brokerWatchlistId);
        assertThat(view.watchlist().getName()).isEqualTo("Technology");
        assertThat(view.watchlist().getSyncStatus()).isEqualTo(Watchlist.SyncStatus.SYNCED);
        assertThat(view.entries()).extracting(WatchlistEntry::getTicker).containsExactly("AAPL", "MSFT");
        verify(alpacaTradingClient).createWatchlist(connection, "Technology", List.of("AAPL", "MSFT"));
    }

    @Test
    void createDoesNotPersistWhenAlpacaRejectsTheRequest() {
        AlpacaConnection connection = connection();
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(watchlistRepository.findByConnectionIdAndName(connection.getConnectionId(), "Technology"))
                .thenReturn(Optional.empty());
        when(alpacaTradingClient.createWatchlist(any(), any(), any()))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ALPACA_WATCHLIST_CREATE_FAILED",
                        "Broker unavailable."));

        assertThatThrownBy(() -> controller().create(
                connection.getConnectionId(),
                UUID.randomUUID(),
                new WatchlistController.Write("Technology", List.of("AAPL"))))
                .isInstanceOf(ApiException.class);

        verify(watchlistRepository, never()).save(any(Watchlist.class));
        verify(watchlistEntryRepository, never()).save(any(WatchlistEntry.class));
    }

    @Test
    void createRejectsDuplicateNormalizedSymbolsBeforeCallingAlpaca() {
        AlpacaConnection connection = connection();
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);

        assertThatThrownBy(() -> controller().create(
                connection.getConnectionId(),
                UUID.randomUUID(),
                new WatchlistController.Write("Technology", List.of("aapl", "AAPL"))))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("WATCHLIST_ENTRY_EXISTS");
                });

        verify(alpacaTradingClient, never()).createWatchlist(any(), any(), any());
    }

    @Test
    void replaceCallsBrokerThenReplacesAllLocalEntries() {
        Watchlist watchlist = watchlist();
        AlpacaConnection connection = connection();
        watchlist.setConnectionId(connection.getConnectionId());
        WatchlistEntry previousEntry = entry(watchlist.getWatchlistId(), "AAPL");
        when(currentUserService.requireOwnedWatchlist(watchlist.getWatchlistId())).thenReturn(watchlist);
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(watchlistRepository.findByConnectionIdAndName(connection.getConnectionId(), "Growth"))
                .thenReturn(Optional.empty());
        when(watchlistEntryRepository.findByWatchlistId(watchlist.getWatchlistId()))
                .thenReturn(List.of(previousEntry), List.of());
        when(watchlistEntryRepository.save(any(WatchlistEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alpacaTradingClient.replaceWatchlist(
                connection,
                watchlist.getAlpacaWatchlistId(),
                "Growth",
                List.of("NVDA")))
                .thenReturn(new AlpacaTradingClient.BrokerWatchlist(
                        watchlist.getAlpacaWatchlistId(), "Growth", List.of()));

        WatchlistController.View view = controller().replace(
                watchlist.getWatchlistId(),
                UUID.randomUUID(),
                new WatchlistController.Write("Growth", List.of("nvda")));

        assertThat(view.watchlist().getName()).isEqualTo("Growth");
        assertThat(view.entries()).extracting(WatchlistEntry::getTicker).containsExactly("NVDA");
        verify(watchlistEntryRepository).deleteAll(List.of(previousEntry));
    }

    @Test
    void addRejectsExistingTickerBeforeCallingAlpaca() {
        Watchlist watchlist = watchlist();
        AlpacaConnection connection = connection();
        watchlist.setConnectionId(connection.getConnectionId());
        when(currentUserService.requireOwnedWatchlist(watchlist.getWatchlistId())).thenReturn(watchlist);
        when(watchlistEntryRepository.findByWatchlistId(watchlist.getWatchlistId()))
                .thenReturn(List.of(entry(watchlist.getWatchlistId(), "AAPL")));

        assertThatThrownBy(() -> controller().add(
                watchlist.getWatchlistId(),
                UUID.randomUUID(),
                new WatchlistController.EntryWrite("aapl")))
                .isInstanceOf(ApiException.class);

        verify(alpacaTradingClient, never()).addWatchlistAsset(any(), any(), any());
    }

    @Test
    void removeDoesNotDeleteLocalEntryWhenAlpacaFails() {
        Watchlist watchlist = watchlist();
        AlpacaConnection connection = connection();
        watchlist.setConnectionId(connection.getConnectionId());
        WatchlistEntry entry = entry(watchlist.getWatchlistId(), "AAPL");
        when(currentUserService.requireOwnedWatchlist(watchlist.getWatchlistId())).thenReturn(watchlist);
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(watchlistEntryRepository.findByWatchlistId(watchlist.getWatchlistId()))
                .thenReturn(List.of(entry));
        doThrow(new ApiException(
                HttpStatus.BAD_GATEWAY,
                "ALPACA_WATCHLIST_UPDATE_FAILED",
                "Broker unavailable."))
                .when(alpacaTradingClient)
                .removeWatchlistAsset(
                        connection,
                        watchlist.getAlpacaWatchlistId(),
                        "AAPL");

        assertThatThrownBy(() -> controller().remove(
                watchlist.getWatchlistId(),
                "AAPL",
                UUID.randomUUID()))
                .isInstanceOf(ApiException.class);

        verify(watchlistEntryRepository, never()).delete(entry);
    }

    @Test
    void deleteDoesNotRemoveLocalDataWhenAlpacaFails() {
        Watchlist watchlist = watchlist();
        AlpacaConnection connection = connection();
        watchlist.setConnectionId(connection.getConnectionId());
        when(currentUserService.requireOwnedWatchlist(watchlist.getWatchlistId())).thenReturn(watchlist);
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        doThrow(new ApiException(
                HttpStatus.BAD_GATEWAY,
                "ALPACA_WATCHLIST_DELETE_FAILED",
                "Broker unavailable."))
                .when(alpacaTradingClient)
                .deleteWatchlist(connection, watchlist.getAlpacaWatchlistId());

        assertThatThrownBy(() -> controller().delete(watchlist.getWatchlistId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class);

        verify(watchlistEntryRepository, never()).deleteAll(any());
        verify(watchlistRepository, never()).delete(watchlist);
    }

    @Test
    void activeChangesOnlyTheLocalSubscriptionFlag() {
        Watchlist watchlist = watchlist();
        WatchlistEntry entry = entry(watchlist.getWatchlistId(), "AAPL");
        when(currentUserService.requireOwnedWatchlist(watchlist.getWatchlistId())).thenReturn(watchlist);
        when(watchlistEntryRepository.findByWatchlistId(watchlist.getWatchlistId()))
                .thenReturn(List.of(entry), List.of(entry));

        WatchlistController.View view = controller().active(
                watchlist.getWatchlistId(),
                "AAPL",
                new WatchlistController.ActiveWrite(false));

        assertThat(entry.isActive()).isFalse();
        assertThat(view.entries()).containsExactly(entry);
        verify(alpacaTradingClient, never()).replaceWatchlist(any(), any(), any(), any());
    }

    private WatchlistController controller() {
        return new WatchlistController(
                currentUserService,
                watchlistRepository,
                watchlistEntryRepository,
                alpacaConnectionRepository,
                alpacaTradingClient);
    }

    private AlpacaConnection connection() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        connection.setAlpacaAccountId(UUID.randomUUID());
        connection.setSecretReference("secret://alpaca/test");
        return connection;
    }

    private Watchlist watchlist() {
        Watchlist watchlist = new Watchlist();
        watchlist.setWatchlistId(UUID.randomUUID());
        watchlist.setConnectionId(UUID.randomUUID());
        watchlist.setAlpacaWatchlistId(UUID.randomUUID());
        watchlist.setName("Technology");
        return watchlist;
    }

    private WatchlistEntry entry(UUID watchlistId, String ticker) {
        WatchlistEntry entry = new WatchlistEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setWatchlistId(watchlistId);
        entry.setTicker(ticker);
        return entry;
    }
}
