package com.ai_got_this.account_service.domain;

import lombok.Data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;



@Data
@Entity
public class OutboxEvent {
    @Id
    private UUID outboxId;

    @Column(nullable=false)
    private String eventType;

    @Column(nullable=false, length=100000)
    private String payload;

    @Column(nullable=false)
    private String status="PENDING";

    private int attempts;

    @Column(nullable=false)
    private Instant createdAt;

    private Instant publishedAt;
}
