package com.ai_got_this.account_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecretAndConnectionFailureServiceTest {

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Test
    void inMemorySecretStoreStoresReadsAndRevokesIndependentTokens() {
        InMemorySecretStore secretStore = new InMemorySecretStore();

        String firstReference = secretStore.store("first-token");
        String secondReference = secretStore.store("second-token");
        secretStore.revoke(firstReference);

        assertThat(firstReference).startsWith("secret://alpaca/");
        assertThat(firstReference).isNotEqualTo(secondReference);
        assertThat(secretStore.read(firstReference)).isNull();
        assertThat(secretStore.read(secondReference)).isEqualTo("second-token");
    }

    @Test
    void connectionFailureMarksMissingTokenAsReauthenticationRequired() {
        AlpacaConnection connection = connection();
        when(alpacaConnectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(connection));

        service().markConnectionFailure(connection.getConnectionId(), "ALPACA_REAUTH_REQUIRED");

        assertThat(connection.getStatus()).isEqualTo(AlpacaConnection.ConnectionStatus.REAUTH_REQUIRED);
        assertThat(connection.getLastErrorCode()).isEqualTo("ALPACA_REAUTH_REQUIRED");
    }

    @Test
    void connectionFailureMarksOtherErrorsAsError() {
        AlpacaConnection connection = connection();
        when(alpacaConnectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(connection));

        service().markConnectionFailure(connection.getConnectionId(), "ALPACA_ACCOUNT_VERIFICATION_FAILED");

        assertThat(connection.getStatus()).isEqualTo(AlpacaConnection.ConnectionStatus.ERROR);
        assertThat(connection.getLastErrorCode()).isEqualTo("ALPACA_ACCOUNT_VERIFICATION_FAILED");
    }

    private ConnectionFailureService service() {
        return new ConnectionFailureService(alpacaConnectionRepository);
    }

    private AlpacaConnection connection() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        return connection;
    }
}
