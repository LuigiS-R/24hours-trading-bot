package com.ai_got_this.account_service.domain;

import lombok.Data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;



@Data
@Entity
public class AlpacaConnection extends AuditedEntity {
    @Id
    private UUID connectionId;

    @Column(nullable=false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Environment environment=Environment.PAPER;

    @Column(nullable=false)
    private UUID alpacaAccountId;
    private String secretReference;
    private String grantedScopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private ConnectionStatus status=ConnectionStatus.PENDING;

    private Instant lastVerifiedAt;
    private String lastErrorCode;

    public enum Environment {
        PAPER
    }

    public enum ConnectionStatus {
        PENDING, CONNECTED, REAUTH_REQUIRED, DISCONNECTED, ERROR
    }
}
