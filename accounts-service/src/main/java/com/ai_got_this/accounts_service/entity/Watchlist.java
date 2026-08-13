package com.ai_got_this.accounts_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Watchlist {

    @Id
    @UuidGenerator
    @Column(name = "watchlist_id", nullable = false, updatable = false)
    private UUID watchlistId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "watchlist_name", nullable = false, length = 64)
    private String watchlistName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
