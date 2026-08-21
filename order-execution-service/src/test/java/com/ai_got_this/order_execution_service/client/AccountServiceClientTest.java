package com.ai_got_this.order_execution_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_service.config.AccountServiceProperties;
import com.ai_got_this.order_execution_service.service.KeycloakService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class AccountServiceClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final KeycloakService keycloakService = mock(KeycloakService.class);
    private final AccountServiceProperties properties = new AccountServiceProperties();
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("http://account-service:8081");
        when(keycloakService.accessToken()).thenReturn("order-execution-jwt");
        client = new AccountServiceClient(restTemplate, properties, keycloakService);
    }

    @Test
    void addsTheServiceJwtWhenRetrievingABrokerCredential() {
        UUID connectionId = UUID.randomUUID();
        when(restTemplate.exchange(
                eq("http://account-service:8081/internal/v1/accounts/{connectionId}/broker-credentials"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountServiceClient.BrokerCredential.class),
                eq(connectionId)))
                .thenReturn(ResponseEntity.ok(new AccountServiceClient.BrokerCredential(connectionId, "oauth-token")));

        client.brokerCredential(connectionId);

        ArgumentCaptor<HttpEntity<?>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://account-service:8081/internal/v1/accounts/{connectionId}/broker-credentials"),
                eq(HttpMethod.GET),
                request.capture(),
                eq(AccountServiceClient.BrokerCredential.class),
                eq(connectionId));
        assertEquals("Bearer order-execution-jwt", request.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    void addsTheServiceJwtWhenRetrievingActiveConnectionCredentials() {
        UUID connectionId = UUID.randomUUID();
        when(restTemplate.exchange(
                eq("http://account-service:8081/internal/v1/order-execution/active-connection-credentials"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(new AccountServiceClient.ActiveConnectionCredential(connectionId, "oauth-token"))));

        client.activeConnectionCredentials();

        ArgumentCaptor<HttpEntity<?>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://account-service:8081/internal/v1/order-execution/active-connection-credentials"),
                eq(HttpMethod.GET),
                request.capture(),
                any(ParameterizedTypeReference.class));
        assertEquals("Bearer order-execution-jwt", request.getValue().getHeaders().getFirst("Authorization"));
    }
}
