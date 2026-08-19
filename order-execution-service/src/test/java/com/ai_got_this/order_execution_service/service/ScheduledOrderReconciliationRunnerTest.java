package com.ai_got_this.order_execution_service.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledOrderReconciliationRunnerTest {
    @Mock
    private AccountServiceClient accountServiceClient;
    @Mock
    private OrderReconciliationService orderReconciliationService;

    @Test
    void reconcilesEachConnectionReturnedByAccountService() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(accountServiceClient.connectionReferences()).thenReturn(List.of(
                new AccountServiceClient.ConnectionReference(first, "CONNECTED"),
                new AccountServiceClient.ConnectionReference(second, "ERROR"),
                new AccountServiceClient.ConnectionReference(UUID.randomUUID(), "DISCONNECTED")));

        runner().reconcileActiveConnections();

        verify(orderReconciliationService).reconcileOrders(first);
        verify(orderReconciliationService).reconcileOrders(second);
    }

    private ScheduledOrderReconciliationRunner runner() {
        return new ScheduledOrderReconciliationRunner(accountServiceClient, orderReconciliationService);
    }
}
