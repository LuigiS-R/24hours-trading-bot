package com.ai_got_this.accounts_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist_entry", uniqueConstraints = @UniqueConstraint(columnNames = {"watchlist_id", "ticker"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistEntry {

    @Id
    @UuidGenerator
    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "watchlist_id", nullable = false)
    private UUID watchlistId;

    @Column(name = "ticker", nullable = false, length = 16)
    private String ticker;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;
}
