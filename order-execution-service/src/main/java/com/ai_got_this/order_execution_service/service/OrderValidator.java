package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class OrderValidator {
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9./-]*");
    private static final Set<String> SIDES = Set.of("buy", "sell");
    private static final Set<String> TIME_IN_FORCE_VALUES = Set.of("day", "gtc", "opg", "cls", "ioc", "fok");

    public Optional<String> validate(Order order) {
        if (order == null) {
            return Optional.of("order is missing");
        }
        if (order.getSymbol() == null || !SYMBOL_PATTERN.matcher(order.getSymbol()).matches()) {
            return Optional.of("symbol is missing or invalid");
        }
        if (order.getQuantity() <= 0) {
            return Optional.of("quantity must be greater than zero");
        }
        if (!contains(SIDES, order.getSide())) {
            return Optional.of("side must be buy or sell");
        }
        if (!"market".equals(normalize(order.getType()))) {
            return Optional.of("only market orders are supported");
        }
        if (!contains(TIME_IN_FORCE_VALUES, order.getTimeInForce())) {
            return Optional.of("timeInForce is missing or unsupported");
        }
        return Optional.empty();
    }

    private boolean contains(Set<String> values, String value) {
        return values.contains(normalize(value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
