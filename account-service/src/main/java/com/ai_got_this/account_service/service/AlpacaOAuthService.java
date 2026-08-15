package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.config.AlpacaProperties;
import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AlpacaOAuthService {
    final AlpacaProperties alpacaProperties;
    final OAuthAuthorizationStateRepository oAuthAuthorizationStateRepository;
    final AlpacaConnectionRepository alpacaConnectionRepository;
    final TradingAccountProjectionRepository tradingAccountProjectionRepository;
    final CurrentUserService currentUserService;
    final AccountReconciliationService accountReconciliationService;
    final AlpacaTradingClient alpacaTradingClient;
    final SecretStore secretStore;
    final WebClient web = WebClient.create();

    public AlpacaOAuthService(
            AlpacaProperties alpacaProperties,
            OAuthAuthorizationStateRepository s,
            AlpacaConnectionRepository c,
            TradingAccountProjectionRepository a,
            CurrentUserService u,
            SecretStore ss,
            AccountReconciliationService accountReconciliationService,
            AlpacaTradingClient alpacaTradingClient) {
        this.alpacaProperties = alpacaProperties;
        oAuthAuthorizationStateRepository = s;
        alpacaConnectionRepository = c;
        tradingAccountProjectionRepository = a;
        currentUserService = u;
        secretStore = ss;
        this.accountReconciliationService = accountReconciliationService;
        this.alpacaTradingClient = alpacaTradingClient;
    }

    public record Authorization(String authorizationUrl, Instant expiresAt) {}

    public record TokenResponse(String access_token, String scope) {}

    @Transactional
    public Authorization createAuthorizationUrl() {
        String state = random();
        OAuthAuthorizationState s = new OAuthAuthorizationState();
        s.setStateId(UUID.randomUUID());
        s.setStateHash(hash(state));
        s.setUserId(currentUserService.getOrProvisionCurrentUser().getUserId());
        s.setEnvironment(AlpacaConnection.Environment.PAPER);
        s.setExpiresAt(Instant.now().plusSeconds(alpacaProperties.oauth().stateTtl()));
        oAuthAuthorizationStateRepository.save(s);
        String url =
                UriComponentsBuilder.fromUriString(alpacaProperties.oauth().authorizeUrl())
                        .queryParam("response_type", "code")
                        .queryParam("client_id", alpacaProperties.oauth().clientId())
                        .queryParam("redirect_uri", alpacaProperties.oauth().redirectUri())
                        .queryParam("state", state)
                        .queryParam("scope", "account:write trading")
                        .queryParam("env", "paper")
                        .build()
                        .encode()
                        .toUriString();
        return new Authorization(url, s.getExpiresAt());
    }

    @Transactional
    public UUID consumeCallback(String code, String state) {
        OAuthAuthorizationState s =
                oAuthAuthorizationStateRepository
                        .findByStateHash(hash(state))
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "OAuth state is invalid."));

        if (s.getConsumedAt() != null)
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "OAuth state has already been used.");

        if (s.getExpiresAt().isBefore(Instant.now()))
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OAUTH_STATE_EXPIRED", "OAuth state has expired.");

        s.setConsumedAt(Instant.now());
        TokenResponse token;
        try {
            token =
                    web.post()
                            .uri(alpacaProperties.oauth().tokenUrl())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(
                                    BodyInserters.fromFormData("grant_type", "authorization_code")
                                            .with("code", code)
                                            .with("client_id", alpacaProperties.oauth().clientId())
                                            .with("client_secret", alpacaProperties.oauth().clientSecret())
                                            .with("redirect_uri", alpacaProperties.oauth().redirectUri()))
                            .retrieve()
                            .bodyToMono(TokenResponse.class)
                            .block();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OAUTH_CODE_EXCHANGE_FAILED", "Could not exchange authorization code.");
        }
        if (token == null || token.access_token() == null)
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OAUTH_CODE_EXCHANGE_FAILED", "Could not exchange authorization code.");

        String secretReference = secretStore.store(token.access_token());
        try {
            AlpacaTradingClient.Account account = verifyAccount(secretReference);
            AlpacaConnection connection = findOrCreateConnection(s, account.id());
            String previousSecretReference = connection.getSecretReference();

            connection.setSecretReference(secretReference);
            connection.setGrantedScopes(defaultScopes(token.scope()));
            connection.setStatus(AlpacaConnection.ConnectionStatus.PENDING);
            alpacaConnectionRepository.save(connection);

            accountReconciliationService.reconcileConnection(connection);
            revokeReplacedSecret(previousSecretReference, secretReference);
            return connection.getConnectionId();
        }
        catch (RuntimeException exception) {
            secretStore.revoke(secretReference);
            throw exception;
        }
    }

    public void disconnectConnection(AlpacaConnection c) {
        if (c.getSecretReference() != null) secretStore.revoke(c.getSecretReference());
        c.setStatus(AlpacaConnection.ConnectionStatus.DISCONNECTED);
        alpacaConnectionRepository.save(c);
    }

    private AlpacaTradingClient.Account verifyAccount(String secretReference) {
        AlpacaConnection verificationConnection = new AlpacaConnection();
        verificationConnection.setSecretReference(secretReference);
        AlpacaTradingClient.Account account = alpacaTradingClient.getAccount(verificationConnection);
        if (account == null || account.id() == null)
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ALPACA_ACCOUNT_VERIFICATION_FAILED", "Unable to verify Alpaca account.");
        return account;
    }

    private AlpacaConnection findOrCreateConnection(OAuthAuthorizationState state, UUID alpacaAccountId) {
        return alpacaConnectionRepository
                .findByUserIdAndEnvironmentAndAlpacaAccountId(
                        state.getUserId(), state.getEnvironment(), alpacaAccountId)
                .orElseGet(
                        () -> {
                            AlpacaConnection connection = new AlpacaConnection();
                            connection.setConnectionId(UUID.randomUUID());
                            connection.setUserId(state.getUserId());
                            connection.setEnvironment(state.getEnvironment());
                            connection.setAlpacaAccountId(alpacaAccountId);
                            return connection;
                        });
    }

    private String defaultScopes(String grantedScopes) {
        return grantedScopes == null ? "account:write trading" : grantedScopes;
    }

    private void revokeReplacedSecret(String previousSecretReference, String newSecretReference) {
        if (previousSecretReference != null && !previousSecretReference.equals(newSecretReference))
            secretStore.revoke(previousSecretReference);
    }

    static String hash(String v) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String random() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
