package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.OrderProjection;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.OrderProjectionRepository;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.service.CurrentUserService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final CurrentUserService currentUserService;
    private final AlpacaConnectionRepository alpacaConnectionRepository;
    private final TradingAccountProjectionRepository tradingAccountProjectionRepository;
    private final PositionProjectionRepository positionProjectionRepository;
    private final OrderProjectionRepository orderProjectionRepository;

    public PortfolioController(
            CurrentUserService currentUserService,
            AlpacaConnectionRepository alpacaConnectionRepository,
            TradingAccountProjectionRepository tradingAccountProjectionRepository,
            PositionProjectionRepository positionProjectionRepository,
            OrderProjectionRepository orderProjectionRepository) {
        this.currentUserService = currentUserService;
        this.alpacaConnectionRepository = alpacaConnectionRepository;
        this.tradingAccountProjectionRepository = tradingAccountProjectionRepository;
        this.positionProjectionRepository = positionProjectionRepository;
        this.orderProjectionRepository = orderProjectionRepository;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        User user = currentUserService.getOrProvisionCurrentUser();
        List<ConnectionDashboard> connections = new ArrayList<>();

        for (AlpacaConnection connection : alpacaConnectionRepository.findByUserId(user.getUserId())) {
            TradingAccountProjection account = tradingAccountProjectionRepository
                    .findById(connection.getConnectionId())
                    .orElse(null);
            List<PositionProjection> positions = positionProjectionRepository
                    .findByConnectionId(connection.getConnectionId());

            connections.add(new ConnectionDashboard(
                    connection.getConnectionId(),
                    connection.getAlpacaAccountId(),
                    connection.getStatus().name(),
                    account,
                    positions,
                    account == null ? null : account.getLastSyncedAt(),
                    freshness(account)));
        }

        return new Dashboard(
                connections,
                total(connections, TradingAccountProjection::getCash),
                total(connections, TradingAccountProjection::getPortfolioValue),
                total(connections, TradingAccountProjection::getEquity),
                earliestSynchronization(connections),
                dashboardFreshness(connections));
    }

    @GetMapping("/alpaca/connections/{id}/portfolio")
    public Portfolio getPortfolio(@PathVariable UUID id) {
        currentUserService.requireOwnedConnection(id);
        TradingAccountProjection account = tradingAccountProjectionRepository.findById(id).orElse(null);

        return new Portfolio(
                account,
                positionProjectionRepository.findByConnectionId(id),
                account == null ? null : account.getLastSyncedAt(),
                freshness(account));
    }

    @GetMapping("/alpaca/connections/{id}/positions")
    public List<PositionProjection> getPositions(@PathVariable UUID id) {
        currentUserService.requireOwnedConnection(id);
        return positionProjectionRepository.findByConnectionId(id);
    }

    @GetMapping("/alpaca/connections/{id}/orders")
    public List<OrderProjection> getOrders(@PathVariable UUID id) {
        currentUserService.requireOwnedConnection(id);
        return orderProjectionRepository.findTop20ByConnectionIdOrderByUpdatedAtDesc(id);
    }

    private BigDecimal total(
            List<ConnectionDashboard> connections,
            java.util.function.Function<TradingAccountProjection, BigDecimal> valueExtractor) {
        return connections.stream()
                .map(ConnectionDashboard::account)
                .filter(java.util.Objects::nonNull)
                .map(valueExtractor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Instant earliestSynchronization(List<ConnectionDashboard> connections) {
        return connections.stream()
                .map(ConnectionDashboard::asOf)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private String dashboardFreshness(List<ConnectionDashboard> connections) {
        if (connections.isEmpty()
                || connections.stream().anyMatch(connection -> "UNAVAILABLE".equals(connection.freshness()))) {
            return "UNAVAILABLE";
        }
        if (connections.stream().anyMatch(connection -> "STALE".equals(connection.freshness()))) {
            return "STALE";
        }
        return "FRESH";
    }

    private String freshness(TradingAccountProjection accountProjection) {
        if (accountProjection == null || accountProjection.getLastSyncedAt() == null) {
            return "UNAVAILABLE";
        }

        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(10));
        if (accountProjection.getLastSyncedAt().isBefore(staleBefore)) {
            return "STALE";
        }

        return "FRESH";
    }

    record Dashboard(
            List<ConnectionDashboard> connections,
            BigDecimal totalCash,
            BigDecimal totalPortfolioValue,
            BigDecimal totalEquity,
            Instant asOf,
            String freshness) {
    }

    record ConnectionDashboard(
            UUID connectionId,
            UUID alpacaAccountId,
            String connectionStatus,
            TradingAccountProjection account,
            List<PositionProjection> positions,
            Instant asOf,
            String freshness) {
    }

    record Portfolio(
            TradingAccountProjection account,
            List<PositionProjection> positions,
            Instant asOf,
            String freshness) {
    }
}
