package com.ai_got_this.account_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
public class OrderProjection {
    @Id private UUID orderId;

    @Column(nullable = false)
    private UUID connectionId;

    @Column(nullable = false, unique = true)
    private UUID alpacaOrderId;

    private String clientOrderId;
    private String symbol;
    private String side;
    private String orderType;
    private String timeInForce;
    private String status;

    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal averageFilledPrice;
    private BigDecimal fees;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant filledAt;
    private Instant cancelledAt;
}
