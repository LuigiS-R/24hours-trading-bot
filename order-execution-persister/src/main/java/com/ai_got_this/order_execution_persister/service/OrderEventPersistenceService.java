package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.dto.OrderEvent;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OrderEventPersistenceService {
    private final OrderProjectionRepository orderProjectionRepository;
    private final OrderProjectionMapper orderProjectionMapper;

    public OrderEventPersistenceService(
            OrderProjectionRepository orderProjectionRepository,
            OrderProjectionMapper orderProjectionMapper) {
        this.orderProjectionRepository = orderProjectionRepository;
        this.orderProjectionMapper = orderProjectionMapper;
    }

    @Transactional
    public void persist(OrderEvent event) {
        UUID alpacaOrderId = alpacaOrderId(event);
        if (alpacaOrderId == null) {
            log.warn("Ignoring order event without a valid Alpaca order ID.");
            return;
        }

        if ("HTTP".equals(event.getStream())) {
            persistHttpCreation(event, alpacaOrderId);
            return;
        }
        if ("trade_updates".equals(event.getStream())) {
            persistTradeUpdate(event, alpacaOrderId);
            return;
        }

        log.warn("Ignoring order event with unsupported stream {}.", event.getStream());
    }

    private void persistHttpCreation(OrderEvent event, UUID alpacaOrderId) {
        if (!"create".equals(event.getEvent())) {
            log.warn("Ignoring unsupported HTTP order event {}.", event.getEvent());
            return;
        }
        if (orderProjectionRepository.findByAlpacaOrderId(alpacaOrderId).isPresent()) {
            log.warn("Ignoring duplicate HTTP creation event for Alpaca order {}.", alpacaOrderId);
            return;
        }

        UUID connectionId = connectionId(event.getConnectionId());
        if (connectionId == null) {
            log.warn("Ignoring HTTP creation event for Alpaca order {} without a valid connection ID.", alpacaOrderId);
            return;
        }

        OrderProjection projection = new OrderProjection();
        projection.setOrderId(UUID.randomUUID());
        projection.setConnectionId(connectionId);
        projection.setAlpacaOrderId(alpacaOrderId);
        if (orderProjectionMapper.applyOrderEvent(projection, event)) {
            orderProjectionRepository.save(projection);
        }
    }

    private void persistTradeUpdate(OrderEvent event, UUID alpacaOrderId) {
        OrderProjection projection = orderProjectionRepository.findByAlpacaOrderId(alpacaOrderId).orElse(null);
        if (projection == null) {
            log.warn("Ignoring trade update for unknown Alpaca order {}.", alpacaOrderId);
            return;
        }

        if (orderProjectionMapper.applyOrderEvent(projection, event)) {
            orderProjectionRepository.save(projection);
        }
    }

    private UUID alpacaOrderId(OrderEvent event) {
        if (event == null || event.getOrder() == null || event.getOrder().getId() == null) {
            return null;
        }
        try {
            return UUID.fromString(event.getOrder().getId());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID connectionId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
