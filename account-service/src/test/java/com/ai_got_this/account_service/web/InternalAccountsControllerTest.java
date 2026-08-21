package com.ai_got_this.account_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.service.ApiException;
import com.ai_got_this.account_service.service.SecretStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalAccountsControllerTest {

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private TradingAccountProjectionRepository tradingAccountProjectionRepository;

    @Mock
    private PositionProjectionRepository positionProjectionRepository;

    @Mock
    private SecretStore secretStore;

    @Test
    void riskSnapshotIsEligibleOnlyForFreshUnblockedConnectedAccounts() {
        AlpacaConnection connection = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        TradingAccountProjection account = new TradingAccountProjection();
        account.setLastSyncedAt(Instant.now().minusSeconds(10));
        when(alpacaConnectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(connection));
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(account));

        InternalAccountsController.RiskSnapshot snapshot = controller().risk(connection.getConnectionId());

        assertThat(snapshot.eligibleForTrading()).isTrue();
        assertThat(snapshot.freshness()).isEqualTo("FRESH");
    }

    @Test
    void riskSnapshotRejectsStaleOrBlockedAccounts() {
        AlpacaConnection connection = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        TradingAccountProjection account = new TradingAccountProjection();
        account.setLastSyncedAt(Instant.now().minusSeconds(601));
        account.setTradingBlocked(true);
        when(alpacaConnectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(connection));
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(account));

        InternalAccountsController.RiskSnapshot snapshot = controller().risk(connection.getConnectionId());

        assertThat(snapshot.eligibleForTrading()).isFalse();
        assertThat(snapshot.freshness()).isEqualTo("STALE");
        assertThat(snapshot.tradingBlocked()).isTrue();
    }

    @Test
    void riskSnapshotRejectsUnknownConnections() {
        UUID connectionId = UUID.randomUUID();
        when(alpacaConnectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().risk(connectionId))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("CONNECTION_NOT_FOUND");
                });
    }

    @Test
    void portfolioSnapshotReturnsTheLocalProjectionAndPositions() {
        AlpacaConnection connection = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        TradingAccountProjection account = new TradingAccountProjection();
        when(alpacaConnectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(connection));
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(account));
        when(positionProjectionRepository.findByConnectionId(connection.getConnectionId()))
                .thenReturn(List.of());

        InternalAccountsController.PortfolioSnapshot snapshot = controller().portfolio(connection.getConnectionId());

        assertThat(snapshot.account()).isSameAs(account);
        assertThat(snapshot.positions()).isEmpty();
    }

    @Test
    void eligiblePortfoliosReturnsOnlyActiveUnblockedAccountsWithMappedPositions() {
        UUID eligibleConnectionId = UUID.randomUUID();
        TradingAccountProjection eligible = new TradingAccountProjection();
        eligible.setConnectionId(eligibleConnectionId);
        eligible.setStatus("ACTIVE");
        eligible.setCash(new BigDecimal("10000.00"));

        TradingAccountProjection blocked = new TradingAccountProjection();
        blocked.setConnectionId(UUID.randomUUID());
        blocked.setStatus("ACTIVE");
        blocked.setTradingBlocked(true);

        PositionProjection position = new PositionProjection();
        position.setSymbol("AAPL");
        position.setQuantity(new BigDecimal("5"));
        position.setAverageEntryPrice(new BigDecimal("175.25"));
        when(tradingAccountProjectionRepository.findAll()).thenReturn(List.of(eligible, blocked));
        when(positionProjectionRepository.findByConnectionId(eligibleConnectionId))
                .thenReturn(List.of(position));

        InternalAccountsController.EligiblePortfolios result = controller().eligiblePortfolios();

        assertThat(result.portfolios()).hasSize(1);
        InternalAccountsController.EligiblePortfolio portfolio = result.portfolios().getFirst();
        assertThat(portfolio.connectionId()).isEqualTo(eligibleConnectionId);
        assertThat(portfolio.cashBalance()).isEqualByComparingTo("10000.00");
        assertThat(portfolio.positions()).containsExactly(
                new InternalAccountsController.Position(
                        "AAPL", new BigDecimal("5"), new BigDecimal("175.25")));
    }

    @Test
    void brokerCredentialsResolveOnlyTheCredentialForTheRequestedConnection() {
        AlpacaConnection connection = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        connection.setSecretReference("secret://alpaca/connection");
        when(alpacaConnectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(connection));
        when(secretStore.read(connection.getSecretReference())).thenReturn("oauth-access-token");

        InternalAccountsController.BrokerCredential credential = controller().credential(connection.getConnectionId());

        assertThat(credential.connectionId()).isEqualTo(connection.getConnectionId());
        assertThat(credential.accessToken()).isEqualTo("oauth-access-token");
    }

    @Test
    void connectionReferencesExposeConnectionIdsAndStatusesWithoutCredentials() {
        AlpacaConnection pending = connection(AlpacaConnection.ConnectionStatus.PENDING);
        AlpacaConnection connected = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        when(alpacaConnectionRepository.findAll()).thenReturn(List.of(pending, connected));

        InternalAccountsController.ConnectionReferences result = controller().connectionReferences();

        assertThat(result.connections()).containsExactly(
                new InternalAccountsController.ConnectionReference(
                        pending.getConnectionId(), AlpacaConnection.ConnectionStatus.PENDING),
                new InternalAccountsController.ConnectionReference(
                connected.getConnectionId(), AlpacaConnection.ConnectionStatus.CONNECTED));
    }

    @Test
    void activeConnectionCredentialsReturnsOnlyConnectedAccountsWithResolvableSecrets() {
        AlpacaConnection connected = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        connected.setSecretReference("secret://alpaca/connected");
        AlpacaConnection disconnected = connection(AlpacaConnection.ConnectionStatus.DISCONNECTED);
        disconnected.setSecretReference("secret://alpaca/disconnected");
        AlpacaConnection missingSecret = connection(AlpacaConnection.ConnectionStatus.CONNECTED);
        missingSecret.setSecretReference("secret://alpaca/missing");
        when(alpacaConnectionRepository.findAll()).thenReturn(List.of(connected, disconnected, missingSecret));
        when(secretStore.read("secret://alpaca/connected")).thenReturn("connected-oauth-token");
        when(secretStore.read("secret://alpaca/missing")).thenReturn(null);

        List<InternalAccountsController.ActiveConnectionCredential> credentials = controller()
                .activeConnectionCredentials();

        assertThat(credentials).containsExactly(
                new InternalAccountsController.ActiveConnectionCredential(
                        connected.getConnectionId(), "connected-oauth-token"));
        org.mockito.Mockito.verify(secretStore, org.mockito.Mockito.never())
                .read("secret://alpaca/disconnected");
    }

    private InternalAccountsController controller() {
        return new InternalAccountsController(
                alpacaConnectionRepository,
                tradingAccountProjectionRepository,
                positionProjectionRepository,
                secretStore);
    }

    private AlpacaConnection connection(AlpacaConnection.ConnectionStatus status) {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        connection.setAlpacaAccountId(UUID.randomUUID());
        connection.setStatus(status);
        return connection;
    }
}
