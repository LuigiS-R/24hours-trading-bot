package com.ai_got_this.accounts_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Data;


@Entity
@Table(name = "accounts")
@Data
@Builder
public class Account {
    @Id
    private String accountId;
    private String userId;
    private String brokerAccountId;
    private boolean isPaper;
    private double cashBalance;
    private String currency;
    private java.time.OffsetDateTime createdAt;
    private java.time.OffsetDateTime updatedAt;
}
