package com.ai_got_this.order_execution_service.client;

import com.ai_got_this.order_execution_service.config.AccountServiceProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountServiceClient {
    private final RestTemplate restTemplate;
    private final AccountServiceProperties properties;

    public AccountServiceClient(RestTemplate restTemplate, AccountServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public BrokerCredential brokerCredential(UUID connectionId) {
        try {
            return restTemplate.exchange(
                    properties.getBaseUrl() + "/internal/v1/accounts/{connectionId}/broker-credentials",
                    HttpMethod.GET,
                    new HttpEntity<Void>(serviceHeaders()),
                    BrokerCredential.class,
                    connectionId).getBody();
        } catch (RestClientException exception) {
            throw new OrderReconciliationException(
                    "ACCOUNT_SERVICE_UNAVAILABLE",
                    "Unable to obtain the broker credential for order reconciliation.",
                    exception);
        }
    }

    public List<ConnectionReference> connectionReferences() {
        try {
            ConnectionReferences response = restTemplate.exchange(
                    properties.getBaseUrl() + "/internal/v1/accounts/connections",
                    HttpMethod.GET,
                    new HttpEntity<Void>(serviceHeaders()),
                    ConnectionReferences.class).getBody();
            return response == null || response.connections() == null ? List.of() : response.connections();
        } catch (RestClientException exception) {
            throw new OrderReconciliationException(
                    "ACCOUNT_SERVICE_UNAVAILABLE",
                    "Unable to obtain connection metadata for order reconciliation.",
                    exception);
        }
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(properties.getServiceToken());
        return headers;
    }

    public record BrokerCredential(UUID connectionId, String accessToken) {
    }

    public record ConnectionReferences(List<ConnectionReference> connections) {
    }

    public record ConnectionReference(UUID connectionId, String status) {
    }
}
