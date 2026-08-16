package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledReconciliationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledReconciliationRunner.class);

    private final AccountReconciliationService accountReconciliationService;
    private final AlpacaConnectionRepository alpacaConnectionRepository;

    public ScheduledReconciliationRunner(
            AccountReconciliationService accountReconciliationService,
            AlpacaConnectionRepository alpacaConnectionRepository) {
        this.accountReconciliationService = accountReconciliationService;
        this.alpacaConnectionRepository = alpacaConnectionRepository;
    }

    @Scheduled(fixedDelayString = "${alpaca.reconciliation-interval:PT5M}")
    public void reconcileActiveConnections() {
        List<AlpacaConnection.ConnectionStatus> eligibleStatuses = List.of(
                AlpacaConnection.ConnectionStatus.PENDING,
                AlpacaConnection.ConnectionStatus.CONNECTED,
                AlpacaConnection.ConnectionStatus.ERROR);

        for (AlpacaConnection connection : alpacaConnectionRepository.findByStatusIn(eligibleStatuses)) {
            reconcile(connection);
        }
    }

    private void reconcile(AlpacaConnection connection) {
        try {
            accountReconciliationService.reconcileConnection(connection);
        } catch (ApiException exception) {
            LOGGER.warn(
                    "Scheduled reconciliation failed for connection {} with code {}.",
                    connection.getConnectionId(),
                    exception.code);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Scheduled reconciliation failed unexpectedly for connection {}.",
                    connection.getConnectionId(),
                    exception);
        }
    }
}
