package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import com.ai_got_this.account_service.service.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WatchlistController {
    final CurrentUserService current;
    final WatchlistRepository watchlists;
    final WatchlistEntryRepository entries;
    final AlpacaConnectionRepository connections;

    public WatchlistController(
            CurrentUserService c,
            WatchlistRepository w,
            WatchlistEntryRepository e,
            AlpacaConnectionRepository a) {
        current = c;
        watchlists = w;
        entries = e;
        connections = a;
    }

    record Write(String name, List<String> symbols) {}

    record EntryWrite(String ticker) {}

    record ActiveWrite(boolean isActive) {}

    record View(Watchlist watchlist, List<WatchlistEntry> entries) {}

    private static final Pattern TICKER = Pattern.compile("[A-Z][A-Z.]{0,15}");

    @GetMapping("/alpaca/connections/{connectionId}/watchlists")
    public List<Watchlist> list(@PathVariable UUID connectionId) {
        current.requireOwnedConnection(connectionId);
        return watchlists.findByConnectionId(connectionId);
    }

    @PostMapping("/alpaca/connections/{connectionId}/watchlists")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public View create(
            @PathVariable UUID connectionId,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody Write write) {
        current.requireOwnedConnection(connectionId);
        Watchlist w = new Watchlist();
        w.setWatchlistId(UUID.randomUUID());
        w.setConnectionId(connectionId);
        w.setAlpacaWatchlistId(UUID.randomUUID());
        w.setName(requireName(write.name()));
        w.setSyncStatus(Watchlist.SyncStatus.PENDING);
        watchlists.save(w);
        return new View(w, replaceEntries(w, write.symbols()));
    }

    @GetMapping("/watchlists/{id}")
    public View get(@PathVariable UUID id) {
        Watchlist w = current.requireOwnedWatchlist(id);
        return new View(w, entries.findByWatchlistId(id));
    }

    @PutMapping("/watchlists/{id}")
    @Transactional
    public View replace(
            @PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key, @RequestBody Write write) {
        Watchlist w = current.requireOwnedWatchlist(id);
        w.setName(requireName(write.name()));
        w.setSyncStatus(Watchlist.SyncStatus.PENDING);
        entries.deleteAll(entries.findByWatchlistId(id));
        return new View(w, replaceEntries(w, write.symbols()));
    }

    @PostMapping("/watchlists/{id}/entries")
    @Transactional
    public View add(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody EntryWrite body) {
        Watchlist w = current.requireOwnedWatchlist(id);
        String ticker = ticker(body.ticker());
        if (entries.findByWatchlistId(id).stream().anyMatch(e -> e.getTicker().equals(ticker)))
            throw new ApiException(
                    HttpStatus.CONFLICT, "WATCHLIST_ENTRY_EXISTS", "Ticker already exists.");
        WatchlistEntry e = new WatchlistEntry();
        e.setEntryId(UUID.randomUUID());
        e.setWatchlistId(id);
        e.setTicker(ticker);
        entries.save(e);
        return new View(w, entries.findByWatchlistId(id));
    }

    @DeleteMapping("/watchlists/{id}/entries/{ticker}")
    @Transactional
    public View remove(
            @PathVariable UUID id,
            @PathVariable String ticker,
            @RequestHeader("Idempotency-Key") UUID key) {
        Watchlist w = current.requireOwnedWatchlist(id);
        WatchlistEntry e =
                entries.findByWatchlistId(id).stream()
                        .filter(x -> x.getTicker().equals(ticker(ticker)))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "WATCHLIST_ENTRY_NOT_FOUND", "Ticker not found."));
        entries.delete(e);
        return new View(w, entries.findByWatchlistId(id));
    }

    @PatchMapping("/watchlists/{id}/entries/{ticker}")
    @Transactional
    public View active(
            @PathVariable UUID id, @PathVariable String ticker, @RequestBody ActiveWrite body) {
        Watchlist w = current.requireOwnedWatchlist(id);
        WatchlistEntry e =
                entries.findByWatchlistId(id).stream()
                        .filter(x -> x.getTicker().equals(ticker(ticker)))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "WATCHLIST_ENTRY_NOT_FOUND", "Ticker not found."));
        e.setActive(body.isActive());
        return new View(w, entries.findByWatchlistId(id));
    }

    @DeleteMapping("/watchlists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key) {
        Watchlist w = current.requireOwnedWatchlist(id);
        w.setSyncStatus(Watchlist.SyncStatus.PENDING_DELETE);
    }

    @GetMapping("/watchlists/active-tickers")
    public Set<String> active() {
        User u = current.getOrProvisionCurrentUser();
        Set<UUID> connectionIds =
                connections.findByUserId(u.getUserId()).stream()
                        .map(c -> c.getConnectionId())
                        .collect(java.util.stream.Collectors.toSet());
        Set<UUID> ids =
                watchlists.findAll().stream()
                        .filter(w -> connectionIds.contains(w.getConnectionId()))
                        .map(w -> w.getWatchlistId())
                        .collect(java.util.stream.Collectors.toSet());
        return ids.isEmpty()
                ? Set.of()
                : entries.findByWatchlistIdInAndIsActiveTrue(ids).stream()
                        .map(e -> e.getTicker())
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private List<WatchlistEntry> replaceEntries(Watchlist w, List<String> symbols) {
        List<WatchlistEntry> saved = new ArrayList<>();
        for (String s : symbols == null ? List.<String>of() : symbols) {
            WatchlistEntry e = new WatchlistEntry();
            e.setEntryId(UUID.randomUUID());
            e.setWatchlistId(w.getWatchlistId());
            e.setTicker(ticker(s));
            saved.add(entries.save(e));
        }
        return saved;
    }

    private String ticker(String s) {
        String v = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        if (!TICKER.matcher(v).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKER", "Ticker is invalid.");
        return v;
    }

    private String requireName(String s) {
        if (s == null || s.isBlank())
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "WATCHLIST_NAME_CONFLICT", "Name is required.");
        return s.trim();
    }
}
