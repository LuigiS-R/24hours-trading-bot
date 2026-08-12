package com.ai_got_this.accounts_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist", uniqueConstraints = @UniqueConstraint(name = "uq_watchlist_user_ticker", columnNames = {"user_id", "ticker"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Watchlist {
    @Id
    @UuidGenerator
    @Column(name = "watchlist_id", nullable = false, updatable = false)
    private UUID watchlistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User userId;

    private String ticker;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private OffsetDateTime addedAt = OffsetDateTime.now();
}