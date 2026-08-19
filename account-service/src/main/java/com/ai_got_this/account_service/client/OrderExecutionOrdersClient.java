package com.ai_got_this.account_service.client;

import com.ai_got_this.account_service.config.OrderExecutionServiceProperties;
import com.ai_got_this.account_service.service.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OrderExecutionOrdersClient {
    private final WebClient webClient;
    private final OrderExecutionServiceProperties properties;

    public OrderExecutionOrdersClient(OrderExecutionServiceProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder().baseUrl(properties.baseUrl()).build();
    }

    public List<OrderProjectionResponse> latestOrders(UUID connectionId) {
        try {
            List<OrderProjectionResponse> orders = webClient.get()
                    .uri("/internal/v1/accounts/{connectionId}/orders", connectionId)
                    .headers(headers -> headers.setBearerAuth(properties.serviceToken()))
                    .retrieve()
                    .bodyToFlux(OrderProjectionResponse.class)
                    .collectList()
                    .block();
            return orders == null ? List.of() : orders;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ORDER_EXECUTION_UNAVAILABLE",
                    "Order data is temporarily unavailable.");
        }
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
    }
}
