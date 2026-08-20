package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.client.AccountServiceClient;
import com.ai_got_this.order_execution_persister.client.AlpacaOrdersClient;
import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReconciliationService {
    private final AccountServiceClient accountServiceClient;
    private final AlpacaOrdersClient alpacaOrdersClient;
    private final OrderProjectionRepository orderProjectionRepository;
    private final OrderProjectionMapper orderProjectionMapper;

    public OrderReconciliationService(
            AccountServiceClient accountServiceClient,
            AlpacaOrdersClient alpacaOrdersClient,
            OrderProjectionRepository orderProjectionRepository,
            OrderProjectionMapper orderProjectionMapper) {
        this.accountServiceClient = accountServiceClient;
        this.alpacaOrdersClient = alpacaOrdersClient;
        this.orderProjectionRepository = orderProjectionRepository;
        this.orderProjectionMapper = orderProjectionMapper;
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
            OrderProjection projection = orderProjectionRepository.findByAlpacaOrderId(brokerOrder.id())
                    .orElseGet(OrderProjection::new);
            orderProjectionMapper.applyBrokerOrder(projection, connectionId, brokerOrder);
            orderProjectionRepository.save(projection);
        }
    }
}
