package com.ai_got_this.order_execution_service.client;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AlpacaOrdersClient {
    private static final ParameterizedTypeReference<List<BrokerOrder>> ORDER_LIST = new ParameterizedTypeReference<>() { };

    private final RestTemplate restTemplate;
    private final AlpacaProperties alpacaProperties;

    public AlpacaOrdersClient(RestTemplate restTemplate, AlpacaProperties alpacaProperties) {
        this.restTemplate = restTemplate;
        this.alpacaProperties = alpacaProperties;
    }

    public List<BrokerOrder> getOrders(String accessToken) {
        try {
            List<BrokerOrder> orders = restTemplate.exchange(
                    alpacaProperties.getPaperTradingBaseUrl() + "/v2/orders?status=all&limit=500",
                    HttpMethod.GET,
                    new HttpEntity<Void>(brokerHeaders(accessToken)),
                    ORDER_LIST).getBody();
            return orders == null ? List.of() : orders;
        } catch (RestClientException exception) {
            throw new OrderReconciliationException(
                    "ALPACA_ORDER_RECONCILIATION_FAILED",
                    "Unable to retrieve Alpaca orders.",
                    exception);
        }
    }

    private HttpHeaders brokerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(accessToken);
        return headers;
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
            Instant created_at,
            Instant updated_at,
            Instant filled_at,
            Instant canceled_at) {
    }
}
