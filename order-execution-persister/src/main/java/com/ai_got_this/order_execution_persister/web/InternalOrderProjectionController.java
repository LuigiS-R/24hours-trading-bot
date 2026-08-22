package com.ai_got_this.order_execution_persister.web;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal read API owned by the order projection service. */
@RestController
@RequestMapping("/internal/v1/accounts")
public class InternalOrderProjectionController {
    private final OrderProjectionRepository orderProjectionRepository;

    public InternalOrderProjectionController(OrderProjectionRepository orderProjectionRepository) {
        this.orderProjectionRepository = orderProjectionRepository;
    }

    @GetMapping("/{connectionId}/orders")
    public List<OrderProjectionResponse> latestOrders(@PathVariable UUID connectionId) {
        return orderProjectionRepository.findTop20ByConnectionIdOrderByUpdatedAtDesc(connectionId).stream()
                .map(OrderProjectionResponse::from)
                .toList();
    }

    public record OrderProjectionResponse(
            UUID orderId,
            UUID connectionId,
            UUID alpacaOrderId,
            String clientOrderId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal filledQuantity,
            BigDecimal averageFilledPrice,
            String orderType,
            String timeInForce,
            String status,
            BigDecimal fees,
            Instant createdAt,
            Instant updatedAt,
            Instant filledAt,
            Instant cancelledAt) {
        private static OrderProjectionResponse from(OrderProjection projection) {
            return new OrderProjectionResponse(
                    projection.getOrderId(),
                    projection.getConnectionId(),
                    projection.getAlpacaOrderId(),
                    projection.getClientOrderId(),
                    projection.getSymbol(),
                    projection.getSide(),
                    projection.getQuantity(),
                    projection.getFilledQuantity(),
                    projection.getAverageFilledPrice(),
                    projection.getOrderType(),
                    projection.getTimeInForce(),
                    projection.getStatus(),
                    projection.getFees(),
                    projection.getCreatedAt(),
                    projection.getUpdatedAt(),
                    projection.getFilledAt(),
                    projection.getCancelledAt());
        }
    }
}
