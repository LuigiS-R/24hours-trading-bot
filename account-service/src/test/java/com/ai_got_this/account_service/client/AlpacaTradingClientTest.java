package com.ai_got_this.account_service.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.config.AlpacaProperties;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.service.ApiException;
import com.ai_got_this.account_service.service.SecretStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlpacaTradingClientTest {

    @Mock
    private SecretStore secretStore;

    @Test
    void everyBrokerOperationRejectsConnectionsWithoutResolvableSecrets() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setSecretReference("secret://alpaca/missing");
        when(secretStore.read(connection.getSecretReference())).thenReturn(null);
        AlpacaTradingClient client = client();

        assertReauthenticationRequired(() -> client.getAccount(connection));
        assertReauthenticationRequired(() -> client.createWatchlist(connection, "Technology", List.of("AAPL")));
        assertReauthenticationRequired(() -> client.replaceWatchlist(
                connection,
                UUID.randomUUID(),
                "Technology",
                List.of("AAPL")));
        assertReauthenticationRequired(() -> client.addWatchlistAsset(
                connection,
                UUID.randomUUID(),
                "AAPL"));
        assertReauthenticationRequired(() -> client.removeWatchlistAsset(
                connection,
                UUID.randomUUID(),
                "AAPL"));
        assertReauthenticationRequired(() -> client.deleteWatchlist(connection, UUID.randomUUID()));
        assertReauthenticationRequired(() -> client.getWatchlists(connection));
    }

    private AlpacaTradingClient client() {
        return new AlpacaTradingClient(
                new AlpacaProperties(
                        new AlpacaProperties.OAuth(null, null, null, null, null, 600),
                        "http://127.0.0.1:1",
                        Duration.ofMinutes(5)),
                secretStore);
    }

    private void assertReauthenticationRequired(ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    org.assertj.core.api.Assertions.assertThat(apiException.code)
                            .isEqualTo("ALPACA_REAUTH_REQUIRED");
                });
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
