package com.ai_got_this.account_service.domain;

import lombok.Data;

import jakarta.persistence.*;
import java.time.Instant;



@Data
@MappedSuperclass
public abstract class AuditedEntity {
    @Column(nullable=false, updatable=false)
    private Instant createdAt;

    @Column(nullable=false)
    private Instant updatedAt;

    @PrePersist
    void created() {
        createdAt=updatedAt=Instant.now();
    }

    @PreUpdate
    void updated() {
        updatedAt=Instant.now();
    }
}
