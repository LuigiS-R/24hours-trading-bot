package com.ai_got_this.accounts_service.repo;

import com.ai_got_this.accounts_service.dto.WatchlistItemResponse;
import com.ai_got_this.accounts_service.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {

    @Query("""
            select new WatchlistItemResponse(
                watchlist.watchlistId,
                watchlist.watchlistName,
                entry.ticker,
                entry.isActive
            )
            from Watchlist watchlist
            join WatchlistEntry entry on entry.watchlistId = watchlist.watchlistId
            where watchlist.userId = :userId
            """)
    List<WatchlistItemResponse> findWatchlistsByUserId(@Param("userId") UUID userId);
}
