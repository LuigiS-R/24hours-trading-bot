package com.ai_got_this.account_service.domain;

import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;



@Data
@Entity
public class TradingAccountProjection {
    @Id
    private UUID connectionId;

    @Column(nullable=false)
    private UUID alpacaAccountId;

    private String accountNumber;
    private String status;
    private String currency;

    private BigDecimal cash;
    private BigDecimal buyingPower;
    private BigDecimal nonMarginableBuyingPower;
    private BigDecimal portfolioValue;
    private BigDecimal equity;
    private BigDecimal lastEquity;
    private BigDecimal longMarketValue;

    private boolean accountBlocked;
    private boolean tradingBlocked;
    private boolean tradeSuspendedByUser;
    private boolean shortingEnabled;

    private Instant lastSyncedAt;
}
