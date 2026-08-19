package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import com.ai_got_this.order_execution_service.client.AlpacaOrdersClient;
import com.ai_got_this.order_execution_service.domain.OrderProjection;
import com.ai_got_this.order_execution_service.repository.OrderProjectionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReconciliationService {
    private final AccountServiceClient accountServiceClient;
    private final AlpacaOrdersClient alpacaOrdersClient;
    private final OrderProjectionRepository orderProjectionRepository;

    public OrderReconciliationService(
            AccountServiceClient accountServiceClient,
            AlpacaOrdersClient alpacaOrdersClient,
            OrderProjectionRepository orderProjectionRepository) {
        this.accountServiceClient = accountServiceClient;
        this.alpacaOrdersClient = alpacaOrdersClient;
        this.orderProjectionRepository = orderProjectionRepository;
    }

    @Transactional
    public void reconcileOrders(UUID connectionId) {
        AccountServiceClient.BrokerCredential credential = accountServiceClient.brokerCredential(connectionId);
        if (credential == null || credential.accessToken() == null || credential.accessToken().isBlank()) {
            throw new IllegalStateException("Account service did not return a broker credential.");
        }

        for (AlpacaOrdersClient.BrokerOrder brokerOrder : alpacaOrdersClient.getOrders(credential.accessToken())) {
            if (brokerOrder.id() == null || brokerOrder.symbol() == null || brokerOrder.updated_at() == null) {
                continue;
            }

            OrderProjection localOrder = orderProjectionRepository.findByAlpacaOrderId(brokerOrder.id())
                    .orElseGet(OrderProjection::new);
            localOrder.setOrderId(localOrder.getOrderId() == null ? UUID.randomUUID() : localOrder.getOrderId());
            localOrder.setConnectionId(connectionId);
            localOrder.setAlpacaOrderId(brokerOrder.id());
            localOrder.setClientOrderId(brokerOrder.client_order_id());
            localOrder.setSymbol(brokerOrder.symbol());
            localOrder.setSide(brokerOrder.side());
            localOrder.setOrderType(brokerOrder.type());
            localOrder.setTimeInForce(brokerOrder.time_in_force());
            localOrder.setStatus(brokerOrder.status());
            localOrder.setQuantity(brokerOrder.qty());
            localOrder.setFilledQuantity(brokerOrder.filled_qty());
            localOrder.setAverageFilledPrice(brokerOrder.filled_avg_price());
            localOrder.setFees(brokerOrder.commission());
            localOrder.setCreatedAt(brokerOrder.created_at());
            localOrder.setUpdatedAt(brokerOrder.updated_at());
            localOrder.setFilledAt(brokerOrder.filled_at());
            localOrder.setCancelledAt(brokerOrder.canceled_at());
            orderProjectionRepository.save(localOrder);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderProjection> latestOrders(UUID connectionId) {
        return orderProjectionRepository.findTop20ByConnectionIdOrderByUpdatedAtDesc(connectionId);
    }
}
