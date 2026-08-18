package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderValidatorTest {
    private final OrderValidator validator = new OrderValidator();

    @Test
    void acceptsASupportedLimitOrder() {
        assertTrue(validator.validate(validOrder()).isEmpty());
    }

    @Test
    void rejectsAMissingOrder() {
        assertEquals("order is missing", validator.validate(null).orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("invalidOrders")
    void rejectsInvalidOrdersWithAnActionableReason(InvalidOrder invalidOrder) {
        Order order = validOrder();
        invalidOrder.mutation().accept(order);

        assertEquals(invalidOrder.reason(), validator.validate(order).orElseThrow());
    }

    private static Stream<InvalidOrder> invalidOrders() {
        return Stream.of(
                new InvalidOrder(order -> order.setSymbol(null), "symbol is missing or invalid"),
                new InvalidOrder(order -> order.setSymbol(" "), "symbol is missing or invalid"),
                new InvalidOrder(order -> order.setSymbol("AAPL$"), "symbol is missing or invalid"),
                new InvalidOrder(order -> order.setQuantity(0), "quantity must be greater than zero"),
                new InvalidOrder(order -> order.setQuantity(-1), "quantity must be greater than zero"),
                new InvalidOrder(order -> order.setSide(null), "side must be buy or sell"),
                new InvalidOrder(order -> order.setSide("hold"), "side must be buy or sell"),
                new InvalidOrder(order -> order.setType(null), "only limit orders are supported"),
                new InvalidOrder(order -> order.setType("market"), "only limit orders are supported"),
                new InvalidOrder(order -> order.setLimitPrice(null), "limitPrice must be greater than zero"),
                new InvalidOrder(order -> order.setLimitPrice(BigDecimal.ZERO), "limitPrice must be greater than zero"),
                new InvalidOrder(order -> order.setLimitPrice(BigDecimal.valueOf(-1)), "limitPrice must be greater than zero")
        );
    }

    private static Order validOrder() {
        Order order = new Order();
        order.setSymbol("AAPL");
        order.setQuantity(1);
        order.setSide("buy");
        order.setType("limit");
        order.setLimitPrice(BigDecimal.valueOf(100));
        return order;
    }

    private record InvalidOrder(Consumer<Order> mutation, String reason) {
    }
}
