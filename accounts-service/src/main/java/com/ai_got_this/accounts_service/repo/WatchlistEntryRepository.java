package com.ai_got_this.accounts_service.repo;

import com.ai_got_this.accounts_service.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, UUID> {

    Optional<WatchlistEntry> findByWatchlistIdAndTicker(UUID watchlistId, String ticker);
}
