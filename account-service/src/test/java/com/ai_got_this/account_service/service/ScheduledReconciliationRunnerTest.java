package com.ai_got_this.account_service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ScheduledReconciliationRunnerTest {

    @Mock
    private AccountReconciliationService accountReconciliationService;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Test
    void runReconcilesEveryEligibleConnectionAndContinuesAfterFailure() {
        AlpacaConnection firstConnection = connection();
        AlpacaConnection secondConnection = connection();
        when(alpacaConnectionRepository.findByStatusIn(any())).thenReturn(List.of(
                firstConnection,
                secondConnection));
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "ALPACA_FAILURE", "Unavailable"))
                .when(accountReconciliationService)
                .reconcileConnection(firstConnection);

        runner().reconcileActiveConnections();

        verify(accountReconciliationService).reconcileConnection(firstConnection);
        verify(accountReconciliationService).reconcileConnection(secondConnection);
        ArgumentCaptor<List<AlpacaConnection.ConnectionStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(alpacaConnectionRepository).findByStatusIn(statusesCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(statusesCaptor.getValue())
                .containsExactly(
                        AlpacaConnection.ConnectionStatus.PENDING,
                        AlpacaConnection.ConnectionStatus.CONNECTED,
                        AlpacaConnection.ConnectionStatus.ERROR);
    }

    private ScheduledReconciliationRunner runner() {
        return new ScheduledReconciliationRunner(
                accountReconciliationService,
                alpacaConnectionRepository);
    }

    private AlpacaConnection connection() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        return connection;
    }
}
