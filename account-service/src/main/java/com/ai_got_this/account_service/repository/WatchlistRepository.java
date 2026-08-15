package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.Watchlist;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
    List<Watchlist> findByConnectionId(UUID id);

    Optional<Watchlist> findByWatchlistIdAndConnectionIdIn(UUID id, Collection<UUID> ids);
}
