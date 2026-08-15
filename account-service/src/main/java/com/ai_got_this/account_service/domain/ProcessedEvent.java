package com.ai_got_this.account_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
public class ProcessedEvent {
    @Id private UUID eventId;

    private String alpacaEventId;

    @Column(nullable = false)
    private Instant processedAt;
}
