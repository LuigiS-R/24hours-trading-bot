package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.config.KeycloakProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Obtains cached Keycloak client-credentials tokens for Account Service's internal calls. */
@Service
public class KeycloakService {
    private static final Duration MAX_EARLY_REFRESH = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final KeycloakProperties properties;
    private final Object tokenLock = new Object();

    private volatile CachedToken cachedToken;

    public KeycloakService(RestTemplate restTemplate, KeycloakProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public String accessToken() {
        CachedToken currentToken = cachedToken;
        if (isUsable(currentToken)) {
            return currentToken.value();
        }

        synchronized (tokenLock) {
            currentToken = cachedToken;
            if (isUsable(currentToken)) {
                return currentToken.value();
            }
            return requestToken();
        }
    }

    private String requestToken() {
        validateConfiguration();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.set("grant_type", "client_credentials");

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    properties.getTokenUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    TokenResponse.class);
            TokenResponse body = response.getBody();
            if (body == null || body.accessToken() == null || body.accessToken().isBlank()
                    || body.expiresIn() == null || body.expiresIn() <= 0) {
                throw new IllegalStateException("Keycloak returned an invalid service-account token response.");
            }

            Duration lifetime = Duration.ofSeconds(body.expiresIn());
            Duration earlyRefresh = lifetime.dividedBy(10);
            if (earlyRefresh.compareTo(MAX_EARLY_REFRESH) > 0) {
                earlyRefresh = MAX_EARLY_REFRESH;
            }
            Instant refreshAt = Instant.now().plus(lifetime.minus(earlyRefresh));
            cachedToken = new CachedToken(body.accessToken(), refreshAt);
            return body.accessToken();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Unable to obtain a Keycloak service-account access token.", exception);
        }
    }

    private boolean isUsable(CachedToken token) {
        return token != null && Instant.now().isBefore(token.refreshAt());
    }

    private void validateConfiguration() {
        StringBuilder missing = new StringBuilder();
        appendIfBlank(missing, "KEYCLOAK_TOKEN_URL", properties.getTokenUrl());
        appendIfBlank(missing, "KEYCLOAK_CLIENT_ID", properties.getClientId());
        appendIfBlank(missing, "KEYCLOAK_CLIENT_SECRET", properties.getClientSecret());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Account Service is missing required Keycloak configuration: " + missing);
        }
    }

    private void appendIfBlank(StringBuilder missing, String name, String value) {
        if (value == null || value.isBlank()) {
            if (!missing.isEmpty()) {
                missing.append(", ");
            }
            missing.append(name);
        }
    }

    private record CachedToken(String value, Instant refreshAt) {
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }
}
