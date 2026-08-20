package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.client.AccountServiceClient;
import com.ai_got_this.order_execution_persister.client.OrderReconciliationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledOrderReconciliationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledOrderReconciliationRunner.class);

    private final AccountServiceClient accountServiceClient;
    private final OrderReconciliationService orderReconciliationService;

    public ScheduledOrderReconciliationRunner(
            AccountServiceClient accountServiceClient,
            OrderReconciliationService orderReconciliationService) {
        this.accountServiceClient = accountServiceClient;
        this.orderReconciliationService = orderReconciliationService;
    }

    @Scheduled(fixedDelayString = "${order-reconciliation.interval:PT5M}")
    public void reconcileActiveConnections() {
        for (AccountServiceClient.ConnectionReference connection : accountServiceClient.connectionReferences()) {
            if (List.of("PENDING", "CONNECTED", "ERROR").contains(connection.status())) {
                reconcile(connection);
            }
        }
    }

    private void reconcile(AccountServiceClient.ConnectionReference connection) {
        try {
            orderReconciliationService.reconcileOrders(connection.connectionId());
        } catch (OrderReconciliationException exception) {
            LOGGER.warn("Order reconciliation failed for connection {} with code {}.", connection.connectionId(), exception.getCode());
        } catch (RuntimeException exception) {
            LOGGER.error("Order reconciliation failed unexpectedly for connection {}.", connection.connectionId(), exception);
        }
    }
}
