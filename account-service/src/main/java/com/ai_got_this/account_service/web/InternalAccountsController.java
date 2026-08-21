package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import java.time.*;
import java.util.*;

import com.ai_got_this.account_service.service.ApiException;
import com.ai_got_this.account_service.service.SecretStore;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class InternalAccountsController {
    final AlpacaConnectionRepository connections;
    final TradingAccountProjectionRepository accounts;
    final PositionProjectionRepository positions;
    final SecretStore secretStore;

    public InternalAccountsController(
            AlpacaConnectionRepository c,
            TradingAccountProjectionRepository a,
            PositionProjectionRepository p,
            SecretStore secretStore) {
        connections = c;
        accounts = a;
        positions = p;
        this.secretStore = secretStore;
    }

    record RiskSnapshot(
            UUID connectionId,
            UUID alpacaAccountId,
            boolean eligibleForTrading,
            java.math.BigDecimal cash,
            java.math.BigDecimal buyingPower,
            java.math.BigDecimal equity,
            boolean accountBlocked,
            boolean tradingBlocked,
            boolean tradeSuspendedByUser,
            AlpacaConnection.ConnectionStatus connectionStatus,
            Instant asOf,
            String freshness) {}

    record PortfolioSnapshot(TradingAccountProjection account, List<PositionProjection> positions) {}

    record EligiblePortfolios(List<EligiblePortfolio> portfolios) {}

    record EligiblePortfolio(UUID connectionId, java.math.BigDecimal cashBalance, List<Position> positions) {}

    record Position(String ticker, java.math.BigDecimal quantity, java.math.BigDecimal averagePrice) {}

    record ConnectionReference(UUID connectionId, AlpacaConnection.ConnectionStatus status) {}

    record ConnectionReferences(List<ConnectionReference> connections) {}

    record ActiveConnectionCredential(UUID connectionId, String accessToken) {}

    @GetMapping("/accounts/{id}/risk-snapshot")
    public RiskSnapshot risk(@PathVariable UUID id) {
        AlpacaConnection c = connection(id);
        TradingAccountProjection a = accounts.findById(id).orElse(null);
        boolean fresh =
                a != null
                        && a.getLastSyncedAt() != null
                        && a.getLastSyncedAt().isAfter(Instant.now().minus(Duration.ofMinutes(10)));
        return new RiskSnapshot(
                id,
                c.getAlpacaAccountId(),
                fresh
                        && c.getStatus() == AlpacaConnection.ConnectionStatus.CONNECTED
                        && !a.isAccountBlocked()
                        && !a.isTradingBlocked()
                        && !a.isTradeSuspendedByUser(),
                a == null ? null : a.getCash(),
                a == null ? null : a.getBuyingPower(),
                a == null ? null : a.getEquity(),
                a != null && a.isAccountBlocked(),
                a != null && a.isTradingBlocked(),
                a != null && a.isTradeSuspendedByUser(),
                c.getStatus(),
                a == null ? null : a.getLastSyncedAt(),
                fresh ? "FRESH" : a == null ? "UNAVAILABLE" : "STALE");
    }

    @GetMapping("/accounts/{id}/portfolio-snapshot")
    public PortfolioSnapshot portfolio(@PathVariable UUID id) {
        connection(id);
        return new PortfolioSnapshot(accounts.findById(id).orElse(null), positions.findByConnectionId(id));
    }

    @GetMapping("/accounts/portfolio")
    public EligiblePortfolios eligiblePortfolios() {
        List<EligiblePortfolio> portfolios = accounts.findAll().stream()
                .filter(this::isEligibleForOrderPlacement)
                .map(account -> new EligiblePortfolio(
                        account.getConnectionId(),
                        account.getCash(),
                        positions.findByConnectionId(account.getConnectionId()).stream()
                                .map(position -> new Position(
                                        position.getSymbol(),
                                        position.getQuantity(),
                                        position.getAverageEntryPrice()))
                                .toList()))
                .toList();
        return new EligiblePortfolios(portfolios);
    }

    @GetMapping("/accounts/connections")
    public ConnectionReferences connectionReferences() {
        return new ConnectionReferences(connections.findAll().stream()
                .map(connection -> new ConnectionReference(connection.getConnectionId(), connection.getStatus()))
                .toList());
    }

    @GetMapping("/order-execution/active-connection-credentials")
    public List<ActiveConnectionCredential> activeConnectionCredentials() {
        return connections.findAll().stream()
                .filter(connection -> connection.getStatus() == AlpacaConnection.ConnectionStatus.CONNECTED)
                .filter(connection -> connection.getSecretReference() != null)
                .map(connection -> {
                    String accessToken = secretStore.read(connection.getSecretReference());
                    return accessToken == null
                            ? null
                            : new ActiveConnectionCredential(connection.getConnectionId(), accessToken);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    record BrokerCredential(UUID connectionId, String accessToken) {}

    @GetMapping("/accounts/{id}/broker-credentials")
    public BrokerCredential credential(@PathVariable UUID id) {
        AlpacaConnection c = connection(id);
        String accessToken = secretStore.read(c.getSecretReference());
        if (accessToken == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALPACA_REAUTH_REQUIRED",
                    "Reconnect your Alpaca paper account to continue.");
        }
        return new BrokerCredential(c.getConnectionId(), accessToken);
    }

    private AlpacaConnection connection(UUID id) {
        return connections
                .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND", "Connection not found."));
    }

    private boolean isEligibleForOrderPlacement(TradingAccountProjection account) {
        return "ACTIVE".equalsIgnoreCase(account.getStatus())
                && !account.isAccountBlocked()
                && !account.isTradingBlocked()
                && !account.isTradeSuspendedByUser();
    }
}
