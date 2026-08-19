package com.ai_got_this.account_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.client.OrderExecutionOrdersClient;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.service.CurrentUserService;
import com.ai_got_this.account_service.service.ApiException;
import org.springframework.http.HttpStatus;
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
class PortfolioControllerTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private TradingAccountProjectionRepository tradingAccountProjectionRepository;

    @Mock
    private PositionProjectionRepository positionProjectionRepository;

    @Mock
    private OrderExecutionOrdersClient orderExecutionOrdersClient;

    @Test
    void dashboardAggregatesOwnedConnectionProjectionsAndUsesOldestTimestamp() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        AlpacaConnection firstConnection = connection();
        AlpacaConnection secondConnection = connection();
        TradingAccountProjection firstAccount = account(
                firstConnection,
                "10.00",
                "15.00",
                "12.00",
                Instant.now().minusSeconds(60));
        TradingAccountProjection secondAccount = account(
                secondConnection,
                "20.00",
                "25.00",
                "22.00",
                Instant.now().minusSeconds(120));
        when(currentUserService.getOrProvisionCurrentUser()).thenReturn(user);
        when(alpacaConnectionRepository.findByUserId(user.getUserId()))
                .thenReturn(List.of(firstConnection, secondConnection));
        when(tradingAccountProjectionRepository.findById(firstConnection.getConnectionId()))
                .thenReturn(Optional.of(firstAccount));
        when(tradingAccountProjectionRepository.findById(secondConnection.getConnectionId()))
                .thenReturn(Optional.of(secondAccount));
        when(positionProjectionRepository.findByConnectionId(firstConnection.getConnectionId()))
                .thenReturn(List.of(position("AAPL")));
        when(positionProjectionRepository.findByConnectionId(secondConnection.getConnectionId()))
                .thenReturn(List.of());

        PortfolioController.Dashboard dashboard = controller().dashboard();

        assertThat(dashboard.connections()).hasSize(2);
        assertThat(dashboard.totalCash()).isEqualByComparingTo("30.00");
        assertThat(dashboard.totalPortfolioValue()).isEqualByComparingTo("40.00");
        assertThat(dashboard.totalEquity()).isEqualByComparingTo("34.00");
        assertThat(dashboard.asOf()).isEqualTo(secondAccount.getLastSyncedAt());
        assertThat(dashboard.freshness()).isEqualTo("FRESH");
        assertThat(dashboard.connections().getFirst().positions()).hasSize(1);
    }

    @Test
    void dashboardIsUnavailableForUsersWithoutConnections() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        when(currentUserService.getOrProvisionCurrentUser()).thenReturn(user);
        when(alpacaConnectionRepository.findByUserId(user.getUserId())).thenReturn(List.of());

        PortfolioController.Dashboard dashboard = controller().dashboard();

        assertThat(dashboard.connections()).isEmpty();
        assertThat(dashboard.totalCash()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dashboard.asOf()).isNull();
        assertThat(dashboard.freshness()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void portfolioMarksProjectionsOlderThanTenMinutesAsStale() {
        AlpacaConnection connection = connection();
        TradingAccountProjection account = account(
                connection,
                "10.00",
                "15.00",
                "12.00",
                Instant.now().minusSeconds(601));
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(tradingAccountProjectionRepository.findById(connection.getConnectionId()))
                .thenReturn(Optional.of(account));
        when(positionProjectionRepository.findByConnectionId(connection.getConnectionId()))
                .thenReturn(List.of());

        PortfolioController.Portfolio portfolio = controller().getPortfolio(connection.getConnectionId());

        assertThat(portfolio.freshness()).isEqualTo("STALE");
    }

    @Test
    void ordersRequiresOwnershipBeforeProxyingToOrderExecution() {
        AlpacaConnection connection = connection();
        OrderExecutionOrdersClient.OrderProjectionResponse order = new OrderExecutionOrdersClient.OrderProjectionResponse(
                UUID.randomUUID(), connection.getConnectionId(), UUID.randomUUID(), "client-1", "AAPL", "buy",
                BigDecimal.ONE, BigDecimal.ZERO, null, "market", "day", "new", null,
                Instant.now(), Instant.now(), null, null);
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(orderExecutionOrdersClient.latestOrders(connection.getConnectionId())).thenReturn(List.of(order));

        List<OrderExecutionOrdersClient.OrderProjectionResponse> result = controller().getOrders(connection.getConnectionId());

        assertThat(result).containsExactly(order);
        verify(currentUserService).requireOwnedConnection(connection.getConnectionId());
        verify(orderExecutionOrdersClient).latestOrders(connection.getConnectionId());
    }

    @Test
    void ordersPropagatesTheProxyUnavailableError() {
        AlpacaConnection connection = connection();
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(orderExecutionOrdersClient.latestOrders(connection.getConnectionId())).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ORDER_EXECUTION_UNAVAILABLE",
                "Order data is temporarily unavailable."));

        assertThatThrownBy(() -> controller().getOrders(connection.getConnectionId()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code).isEqualTo("ORDER_EXECUTION_UNAVAILABLE"));
    }

    private PortfolioController controller() {
        return new PortfolioController(
                currentUserService,
                alpacaConnectionRepository,
                tradingAccountProjectionRepository,
                positionProjectionRepository,
                orderExecutionOrdersClient);
    }

    private AlpacaConnection connection() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        connection.setAlpacaAccountId(UUID.randomUUID());
        connection.setStatus(AlpacaConnection.ConnectionStatus.CONNECTED);
        return connection;
    }

    private TradingAccountProjection account(
            AlpacaConnection connection,
            String cash,
            String portfolioValue,
            String equity,
            Instant lastSyncedAt) {
        TradingAccountProjection account = new TradingAccountProjection();
        account.setConnectionId(connection.getConnectionId());
        account.setAlpacaAccountId(connection.getAlpacaAccountId());
        account.setCash(new BigDecimal(cash));
        account.setPortfolioValue(new BigDecimal(portfolioValue));
        account.setEquity(new BigDecimal(equity));
        account.setLastSyncedAt(lastSyncedAt);
        return account;
    }

    private PositionProjection position(String symbol) {
        PositionProjection position = new PositionProjection();
        position.setPositionId(UUID.randomUUID());
        position.setSymbol(symbol);
        position.setQuantity(BigDecimal.ONE);
        return position;
    }
}
