package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import com.ai_got_this.account_service.service.ApiException;
import com.ai_got_this.account_service.service.CurrentUserService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WatchlistController {

    private static final Pattern TICKER = Pattern.compile("[A-Z][A-Z.]{0,15}");

    private final CurrentUserService currentUserService;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistEntryRepository watchlistEntryRepository;
    private final AlpacaConnectionRepository alpacaConnectionRepository;
    private final AlpacaTradingClient alpacaTradingClient;

    public WatchlistController(
            CurrentUserService currentUserService,
            WatchlistRepository watchlistRepository,
            WatchlistEntryRepository watchlistEntryRepository,
            AlpacaConnectionRepository alpacaConnectionRepository,
            AlpacaTradingClient alpacaTradingClient) {
        this.currentUserService = currentUserService;
        this.watchlistRepository = watchlistRepository;
        this.watchlistEntryRepository = watchlistEntryRepository;
        this.alpacaConnectionRepository = alpacaConnectionRepository;
        this.alpacaTradingClient = alpacaTradingClient;
    }

    @GetMapping("/alpaca/connections/{connectionId}/watchlists")
    public List<Watchlist> list(@PathVariable UUID connectionId) {
        currentUserService.requireOwnedConnection(connectionId);
        return watchlistRepository.findByConnectionId(connectionId);
    }

    @PostMapping("/alpaca/connections/{connectionId}/watchlists")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public View create(
            @PathVariable UUID connectionId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody Write write) {
        AlpacaConnection connection = currentUserService.requireOwnedConnection(connectionId);
        String name = requireName(write.name());
        List<String> symbols = normalizeTickers(write.symbols());

        requireAvailableName(connectionId, name, null);
        AlpacaTradingClient.BrokerWatchlist brokerWatchlist =
                alpacaTradingClient.createWatchlist(connection, name, symbols);

        Watchlist watchlist = new Watchlist();
        watchlist.setWatchlistId(UUID.randomUUID());
        watchlist.setConnectionId(connectionId);
        watchlist.setAlpacaWatchlistId(brokerWatchlist.id());
        watchlist.setName(name);
        watchlist.setSyncStatus(Watchlist.SyncStatus.SYNCED);
        watchlist.setLastSyncErrorCode(null);
        watchlistRepository.save(watchlist);

        return new View(watchlist, saveEntries(watchlist, symbols));
    }

    @GetMapping("/watchlists/{id}")
    public View get(@PathVariable UUID id) {
        Watchlist watchlist = currentUserService.requireOwnedWatchlist(id);
        return viewOf(watchlist);
    }

    @PutMapping("/watchlists/{id}")
    @Transactional
    public View replace(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody Write write) {
        Watchlist watchlist = currentUserService.requireOwnedWatchlist(id);
        AlpacaConnection connection = currentUserService.requireOwnedConnection(watchlist.getConnectionId());
        String name = requireName(write.name());
        List<String> symbols = normalizeTickers(write.symbols());

        requireAvailableName(connection.getConnectionId(), name, watchlist.getWatchlistId());
        alpacaTradingClient.replaceWatchlist(
                connection,
                watchlist.getAlpacaWatchlistId(),
                name,
                symbols);

        watchlist.setName(name);
        watchlist.setSyncStatus(Watchlist.SyncStatus.SYNCED);
        watchlist.setLastSyncErrorCode(null);
        watchlistEntryRepository.deleteAll(watchlistEntryRepository.findByWatchlistId(id));

        return new View(watchlist, saveEntries(watchlist, symbols));
    }

    @PostMapping("/watchlists/{id}/entries")
    @Transactional
    public View add(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody EntryWrite body) {
        Watchlist watchlist = currentUserService.requireOwnedWatchlist(id);
        String ticker = ticker(body.ticker());
        List<WatchlistEntry> currentEntries = watchlistEntryRepository.findByWatchlistId(id);

        if (currentEntries.stream().anyMatch(entry -> entry.getTicker().equals(ticker))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WATCHLIST_ENTRY_EXISTS",
                    "Ticker already exists.");
        }

        AlpacaConnection connection = currentUserService.requireOwnedConnection(watchlist.getConnectionId());
        alpacaTradingClient.addWatchlistAsset(
                connection,
                watchlist.getAlpacaWatchlistId(),
                ticker);

        WatchlistEntry entry = new WatchlistEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setWatchlistId(id);
        entry.setTicker(ticker);
        watchlistEntryRepository.save(entry);

        return viewOf(watchlist);
    }

    @DeleteMapping("/watchlists/{id}/entries/{ticker}")
    @Transactional
    public View remove(
            @PathVariable UUID id,
            @PathVariable String ticker,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        Watchlist watchlist = currentUserService.requireOwnedWatchlist(id);
        String normalizedTicker = ticker(ticker);
        WatchlistEntry entry = findEntry(watchlist, normalizedTicker);

        AlpacaConnection connection = currentUserService.requireOwnedConnection(watchlist.getConnectionId());
        alpacaTradingClient.removeWatchlistAsset(
                connection,
                watchlist.getAlpacaWatchlistId(),
                normalizedTicker);
        watchlistEntryRepository.delete(entry);

        return viewOf(watchlist);
    }

    @PatchMapping("/watchlists/{id}/entries/{ticker}")
    @Transactional
    public View active(
            @PathVariable UUID id,
            @PathVariable String ticker,
            @RequestBody ActiveWrite body) {
        Watchlist watchlist = currentUserService.requireOwnedWatchlist(id);
        WatchlistEntry entry = findEntry(watchlist, ticker(ticker));
        entry.setActive(body.isActive());

        return viewOf(watchlist);
    }

    @DeleteMapping("/watchlists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        Watchlist watchlist = currentUserService.requireOwnedWatchlist(id);
        AlpacaConnection connection = currentUserService.requireOwnedConnection(watchlist.getConnectionId());

        alpacaTradingClient.deleteWatchlist(connection, watchlist.getAlpacaWatchlistId());
        watchlistEntryRepository.deleteAll(watchlistEntryRepository.findByWatchlistId(id));
        watchlistRepository.delete(watchlist);
    }

    @GetMapping("/watchlists/active-tickers")
    public Set<String> active() {
        User user = currentUserService.getOrProvisionCurrentUser();
        Set<UUID> connectionIds = alpacaConnectionRepository.findByUserId(user.getUserId())
                .stream()
                .map(AlpacaConnection::getConnectionId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> watchlistIds = watchlistRepository.findAll()
                .stream()
                .filter(watchlist -> connectionIds.contains(watchlist.getConnectionId()))
                .map(Watchlist::getWatchlistId)
                .collect(java.util.stream.Collectors.toSet());

        if (watchlistIds.isEmpty()) {
            return Set.of();
        }

        return watchlistEntryRepository.findByWatchlistIdInAndIsActiveTrue(watchlistIds)
                .stream()
                .map(WatchlistEntry::getTicker)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private View viewOf(Watchlist watchlist) {
        return new View(
                watchlist,
                watchlistEntryRepository.findByWatchlistId(watchlist.getWatchlistId()));
    }

    private List<WatchlistEntry> saveEntries(Watchlist watchlist, List<String> symbols) {
        List<WatchlistEntry> savedEntries = new ArrayList<>();
        for (String symbol : symbols) {
            WatchlistEntry entry = new WatchlistEntry();
            entry.setEntryId(UUID.randomUUID());
            entry.setWatchlistId(watchlist.getWatchlistId());
            entry.setTicker(symbol);
            savedEntries.add(watchlistEntryRepository.save(entry));
        }
        return savedEntries;
    }

    private WatchlistEntry findEntry(Watchlist watchlist, String ticker) {
        return watchlistEntryRepository.findByWatchlistId(watchlist.getWatchlistId())
                .stream()
                .filter(entry -> entry.getTicker().equals(ticker))
                .findFirst()
                .orElseThrow(
                        () -> new ApiException(
                                HttpStatus.NOT_FOUND,
                                "WATCHLIST_ENTRY_NOT_FOUND",
                                "Ticker not found."));
    }

    private void requireAvailableName(UUID connectionId, String name, UUID existingWatchlistId) {
        watchlistRepository.findByConnectionIdAndName(connectionId, name)
                .filter(watchlist -> !watchlist.getWatchlistId().equals(existingWatchlistId))
                .ifPresent(watchlist -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "WATCHLIST_NAME_CONFLICT",
                            "A watchlist with this name already exists.");
                });
    }

    private List<String> normalizeTickers(List<String> symbols) {
        List<String> normalizedTickers = new ArrayList<>();
        Set<String> uniqueTickers = new HashSet<>();

        for (String symbol : symbols == null ? List.<String>of() : symbols) {
            String ticker = ticker(symbol);
            if (!uniqueTickers.add(ticker)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "WATCHLIST_ENTRY_EXISTS",
                        "Ticker already exists.");
            }
            normalizedTickers.add(ticker);
        }

        return normalizedTickers;
    }

    private String ticker(String value) {
        String normalizedTicker = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TICKER.matcher(normalizedTicker).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TICKER",
                    "Ticker is invalid.");
        }
        return normalizedTicker;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WATCHLIST_NAME_CONFLICT",
                    "Name is required.");
        }
        return value.trim();
    }

    record Write(String name, List<String> symbols) {
    }

    record EntryWrite(String ticker) {
    }

    record ActiveWrite(boolean isActive) {
    }

    record View(Watchlist watchlist, List<WatchlistEntry> entries) {
    }
}
