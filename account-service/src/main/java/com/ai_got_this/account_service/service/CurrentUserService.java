package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
    final UserRepository users;
    final AlpacaConnectionRepository connections;
    final WatchlistRepository watchlists;

    public CurrentUserService(UserRepository u, AlpacaConnectionRepository c, WatchlistRepository w) {
        users = u;
        connections = c;
        watchlists = w;
    }

    @Transactional
    public User getOrProvisionCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid user token is required.");
        return users
                .findByExternalSubject(jwt.getSubject())
                .orElseGet(
                        () -> {
                            User u = new User();
                            u.setUserId(UUID.randomUUID());
                            u.setExternalSubject(jwt.getSubject());
                            u.setEmail(jwt.getClaimAsString("email"));
                            u.setDisplayName(jwt.getClaimAsString("preferred_username"));
                            return users.save(u);
                        });
    }

    @Transactional
    public User updateDisplayName(String displayName) {
        User u = getOrProvisionCurrentUser();
        u.setDisplayName(displayName);
        return users.save(u);
    }

    public AlpacaConnection requireOwnedConnection(UUID id) {
        User u = getOrProvisionCurrentUser();
        return connections
                .findByConnectionIdAndUserId(id, u.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND", "Connection not found."));
    }

    public Watchlist requireOwnedWatchlist(UUID id) {
        User u = getOrProvisionCurrentUser();
        List<UUID> ids =
                connections.findByUserId(u.getUserId()).stream().map(c -> c.getConnectionId()).toList();
        return watchlists
                .findByWatchlistIdAndConnectionIdIn(id, ids)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "WATCHLIST_NOT_FOUND", "Watchlist not found."));
    }
}
