package com.ai_got_this.accounts_service.service;

import com.ai_got_this.accounts_service.repo.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WatchlistService {
    private final WatchlistRepository watchlistRepository;

    public WatchlistService(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = watchlistRepository;
    }

    public List<UUID> getUsers(String ticker) {
        return watchlistRepository.findUserIdByTickerAndIsActiveTrue(ticker);
    }
}
