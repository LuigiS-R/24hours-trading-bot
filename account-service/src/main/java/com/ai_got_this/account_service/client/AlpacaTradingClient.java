package com.ai_got_this.account_service.client;

import com.ai_got_this.account_service.config.AlpacaProperties;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.service.ApiException;
import com.ai_got_this.account_service.service.SecretStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AlpacaTradingClient {

    private final WebClient webClient;
    private final SecretStore secretStore;

    public AlpacaTradingClient(AlpacaProperties alpacaProperties, SecretStore secretStore) {
        this.webClient = WebClient.builder()
                .baseUrl(alpacaProperties.paperTradingBaseUrl())
                .build();
        this.secretStore = secretStore;
    }

    public Account getAccount(AlpacaConnection connection) {
        return executeBrokerCall(
                "ALPACA_ACCOUNT_VERIFICATION_FAILED",
                "Unable to verify Alpaca account.",
                () -> authorizedRequest(HttpMethod.GET, connection, "/v2/account")
                        .retrieve()
                        .bodyToMono(Account.class)
                        .block());
    }

    public BrokerWatchlist createWatchlist(
            AlpacaConnection connection,
            String name,
            List<String> symbols) {
        BrokerWatchlist watchlist = executeBrokerCall(
                "ALPACA_WATCHLIST_CREATE_FAILED",
                "Unable to create the Alpaca watchlist.",
                () -> authorizedRequest(HttpMethod.POST, connection, "/v2/watchlists")
                        .bodyValue(new WatchlistWrite(name, symbols))
                        .retrieve()
                        .bodyToMono(BrokerWatchlist.class)
                        .block());

        if (watchlist == null || watchlist.id() == null) {
            throw brokerFailure(
                    "ALPACA_WATCHLIST_CREATE_FAILED",
                    "Alpaca did not return a watchlist identifier.");
        }

        return watchlist;
    }

    public BrokerWatchlist replaceWatchlist(
            AlpacaConnection connection,
            UUID watchlistId,
            String name,
            List<String> symbols) {
        BrokerWatchlist watchlist = executeBrokerCall(
                "ALPACA_WATCHLIST_UPDATE_FAILED",
                "Unable to update the Alpaca watchlist.",
                () -> authorizedRequest(HttpMethod.PUT, connection, "/v2/watchlists/{watchlistId}", watchlistId)
                        .bodyValue(new WatchlistWrite(name, symbols))
                        .retrieve()
                        .bodyToMono(BrokerWatchlist.class)
                        .block());

        if (watchlist == null || watchlist.id() == null) {
            throw brokerFailure(
                    "ALPACA_WATCHLIST_UPDATE_FAILED",
                    "Alpaca did not return an updated watchlist.");
        }

        return watchlist;
    }

    public void addWatchlistAsset(
            AlpacaConnection connection,
            UUID watchlistId,
            String symbol) {
        executeBrokerCall(
                "ALPACA_WATCHLIST_UPDATE_FAILED",
                "Unable to add the asset to the Alpaca watchlist.",
                () -> authorizedRequest(HttpMethod.POST, connection, "/v2/watchlists/{watchlistId}", watchlistId)
                        .bodyValue(new AssetWrite(symbol))
                        .retrieve()
                        .bodyToMono(BrokerWatchlist.class)
                        .block());
    }

    public void removeWatchlistAsset(
            AlpacaConnection connection,
            UUID watchlistId,
            String symbol) {
        executeBrokerCall(
                "ALPACA_WATCHLIST_UPDATE_FAILED",
                "Unable to remove the asset from the Alpaca watchlist.",
                () -> authorizedRequest(
                                HttpMethod.DELETE,
                                connection,
                                "/v2/watchlists/{watchlistId}/{symbol}",
                                watchlistId,
                                symbol)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block());
    }

    public void deleteWatchlist(AlpacaConnection connection, UUID watchlistId) {
        executeBrokerCall(
                "ALPACA_WATCHLIST_DELETE_FAILED",
                "Unable to delete the Alpaca watchlist.",
                () -> authorizedRequest(HttpMethod.DELETE, connection, "/v2/watchlists/{watchlistId}", watchlistId)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block());
    }

    public List<BrokerWatchlist> getWatchlists(AlpacaConnection connection) {
        List<BrokerWatchlist> watchlists = executeBrokerCall(
                "ALPACA_WATCHLIST_RECONCILIATION_FAILED",
                "Unable to retrieve Alpaca watchlists.",
                () -> authorizedRequest(HttpMethod.GET, connection, "/v2/watchlists")
                        .retrieve()
                        .bodyToFlux(BrokerWatchlist.class)
                        .collectList()
                        .block());

        return watchlists == null ? List.of() : watchlists;
    }

    public List<BrokerPosition> getPositions(AlpacaConnection connection) {
        List<BrokerPosition> positions = executeBrokerCall(
                "ALPACA_POSITION_RECONCILIATION_FAILED",
                "Unable to retrieve Alpaca positions.",
                () -> authorizedRequest(HttpMethod.GET, connection, "/v2/positions")
                        .retrieve()
                        .bodyToFlux(BrokerPosition.class)
                        .collectList()
                        .block());

        return positions == null ? List.of() : positions;
    }

    /**
     * Retrieves the most recent complete order view available from Alpaca.  Alpaca caps one
     * response at 500 orders, which is also its documented maximum page size.
     */
    public List<BrokerOrder> getOrders(AlpacaConnection connection) {
        List<BrokerOrder> orders = executeBrokerCall(
                "ALPACA_ORDER_RECONCILIATION_FAILED",
                "Unable to retrieve Alpaca orders.",
                () -> authorizedRequest(HttpMethod.GET, connection, "/v2/orders?status=all&limit=500")
                        .retrieve()
                        .bodyToFlux(BrokerOrder.class)
                        .collectList()
                        .block());

        return orders == null ? List.of() : orders;
    }

    private WebClient.RequestBodySpec authorizedRequest(
            HttpMethod method,
            AlpacaConnection connection,
            String uri,
            Object... uriVariables) {
        String accessToken = secretStore.read(connection.getSecretReference());
        if (accessToken == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALPACA_REAUTH_REQUIRED",
                    "Reconnect your Alpaca paper account to continue.");
        }

        return webClient.method(method)
                .uri(uri, uriVariables)
                .headers(headers -> headers.setBearerAuth(accessToken));
    }

    private <T> T executeBrokerCall(
            String errorCode,
            String errorMessage,
            Supplier<T> brokerCall) {
        try {
            return brokerCall.get();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw brokerFailure(errorCode, errorMessage);
        }
    }

    private ApiException brokerFailure(String errorCode, String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, errorCode, message);
    }

    public record Account(
            UUID id,
            String account_number,
            String status,
            String currency,
            BigDecimal cash,
            BigDecimal buying_power,
            BigDecimal non_marginable_buying_power,
            BigDecimal portfolio_value,
            BigDecimal equity,
            BigDecimal last_equity,
            BigDecimal long_market_value,
            boolean account_blocked,
            boolean trading_blocked,
            boolean trade_suspended_by_user,
            boolean shorting_enabled) {
    }

    public record BrokerWatchlist(UUID id, String name, List<BrokerAsset> assets) {
    }

    public record BrokerAsset(String symbol) {
    }

    public record BrokerPosition(
            UUID asset_id,
            String symbol,
            BigDecimal qty,
            BigDecimal qty_available,
            String side,
            BigDecimal avg_entry_price,
            BigDecimal cost_basis,
            BigDecimal current_price,
            BigDecimal market_value,
            BigDecimal unrealized_pl) {
    }

    public record BrokerOrder(
            UUID id,
            String client_order_id,
            String symbol,
            String side,
            String type,
            String time_in_force,
            String status,
            BigDecimal qty,
            BigDecimal filled_qty,
            BigDecimal filled_avg_price,
            BigDecimal commission,
            java.time.Instant created_at,
            java.time.Instant updated_at,
            java.time.Instant filled_at,
            java.time.Instant canceled_at) {
    }

    private record WatchlistWrite(String name, List<String> symbols) {
    }

    private record AssetWrite(String symbol) {
    }
}
