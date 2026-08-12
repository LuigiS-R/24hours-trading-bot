package com.ai_got_this.accounts_service.repo;

import com.ai_got_this.accounts_service.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
    List<UUID> findUserIdByTickerAndIsActiveTrue(String ticker);
}
