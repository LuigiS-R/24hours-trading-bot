package com.ai_got_this.order_execution_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.order_execution_service.config.KeycloakProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

class KeycloakServiceTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final KeycloakProperties properties = configuredProperties();
    private final KeycloakService keycloakService = new KeycloakService(restTemplate, properties);

    @Test
    void requestsAndCachesAClientCredentialsToken() {
        when(restTemplate.exchange(
                eq("https://keycloak.example/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(KeycloakService.TokenResponse.class)))
                .thenReturn(ResponseEntity.ok(new KeycloakService.TokenResponse("service-jwt", 300L)));

        assertEquals("service-jwt", keycloakService.accessToken());
        assertEquals("service-jwt", keycloakService.accessToken());

        ArgumentCaptor<HttpEntity<?>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://keycloak.example/token"),
                eq(HttpMethod.POST),
                request.capture(),
                eq(KeycloakService.TokenResponse.class));
        assertEquals("Basic b3JkZXItZXhlY3V0aW9uLXNlcnZpY2U6c2VjcmV0", request.getValue().getHeaders().getFirst("Authorization"));
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> form = (MultiValueMap<String, String>) request.getValue().getBody();
        assertEquals("client_credentials", form.getFirst("grant_type"));
    }

    @Test
    void rejectsMissingConfigurationBeforeCallingKeycloak() {
        properties.setClientSecret(" ");

        IllegalStateException exception = assertThrows(IllegalStateException.class, keycloakService::accessToken);

        assertEquals(
                "Order Execution service is missing required Keycloak configuration: KEYCLOAK_CLIENT_SECRET",
                exception.getMessage());
    }

    private static KeycloakProperties configuredProperties() {
        KeycloakProperties configured = new KeycloakProperties();
        configured.setTokenUrl("https://keycloak.example/token");
        configured.setClientId("order-execution-service");
        configured.setClientSecret("secret");
        return configured;
    }
}
