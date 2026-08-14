package com.ai_got_this.account_service.domain;

import lombok.Data;

import jakarta.persistence.*;
import java.util.UUID;



@Data
@Entity
public class Watchlist extends AuditedEntity {
    @Id
    private UUID watchlistId;

    @Column(nullable=false)
    private UUID connectionId;

    @Column(nullable=false, unique=true)
    private UUID alpacaWatchlistId;

    @Column(nullable=false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private SyncStatus syncStatus=SyncStatus.SYNCED;

    private String lastSyncErrorCode;

    public enum SyncStatus {
        SYNCED, PENDING, SYNC_FAILED, PENDING_DELETE
    }
}
