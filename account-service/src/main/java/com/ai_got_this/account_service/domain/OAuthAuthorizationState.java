package com.ai_got_this.account_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
public class OAuthAuthorizationState {
    @Id private UUID stateId;

    @Column(nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private AlpacaConnection.Environment environment;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void created() {
        createdAt = Instant.now();
    }
}
