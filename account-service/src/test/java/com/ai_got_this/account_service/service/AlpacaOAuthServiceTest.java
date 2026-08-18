package com.ai_got_this.account_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.config.AlpacaProperties;
import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.OAuthAuthorizationState;
import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.OAuthAuthorizationStateRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlpacaOAuthServiceTest {

    @Mock
    private OAuthAuthorizationStateRepository authorizationStateRepository;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private TradingAccountProjectionRepository tradingAccountProjectionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private SecretStore secretStore;

    @Mock
    private AccountReconciliationService accountReconciliationService;

    @Mock
    private AlpacaTradingClient alpacaTradingClient;

    @Test
    void createAuthorizationUrlStoresOnlyHashedStateAndBuildsExpectedUrl() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        when(currentUserService.getOrProvisionCurrentUser()).thenReturn(user);

        AlpacaOAuthService.Authorization authorization = service().createAuthorizationUrl();

        ArgumentCaptor<OAuthAuthorizationState> stateCaptor =
                ArgumentCaptor.forClass(OAuthAuthorizationState.class);
        verify(authorizationStateRepository).save(stateCaptor.capture());
        OAuthAuthorizationState savedState = stateCaptor.getValue();

        assertThat(savedState.getStateHash()).hasSize(64);
        assertThat(savedState.getUserId()).isEqualTo(user.getUserId());
        assertThat(savedState.getEnvironment()).isEqualTo(AlpacaConnection.Environment.PAPER);
        assertThat(savedState.getExpiresAt()).isAfter(Instant.now());
        assertThat(authorization.authorizationUrl())
                .contains("response_type=code")
                .contains("client_id=test-client")
                .contains("env=paper");
        assertThat(authorization.expiresAt()).isEqualTo(savedState.getExpiresAt());
    }

    @Test
    void consumeCallbackRejectsUnknownStateBeforeAnyTokenExchange() {
        when(authorizationStateRepository.findByStateHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().consumeCallback("code", "state"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("OAUTH_STATE_INVALID");
                });
    }

    @Test
    void consumeCallbackRejectsAlreadyConsumedStateBeforeAnyTokenExchange() {
        OAuthAuthorizationState state = validState();
        state.setConsumedAt(Instant.now());
        when(authorizationStateRepository.findByStateHash(any())).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service().consumeCallback("code", "state"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("OAUTH_STATE_INVALID");
                });
    }

    @Test
    void consumeCallbackRejectsExpiredStateBeforeAnyTokenExchange() {
        OAuthAuthorizationState state = validState();
        state.setExpiresAt(Instant.now().minusSeconds(1));
        when(authorizationStateRepository.findByStateHash(any())).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service().consumeCallback("code", "state"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("OAUTH_STATE_EXPIRED");
                });
    }

    @Test
    void hashIsDeterministicAndRandomStateIsUrlSafe() {
        assertThat(AlpacaOAuthService.hash("state-value"))
                .isEqualTo(AlpacaOAuthService.hash("state-value"))
                .hasSize(64);
        assertThat(AlpacaOAuthService.random())
                .matches("[A-Za-z0-9_-]{43}");
    }

    private AlpacaOAuthService service() {
        return new AlpacaOAuthService(
                new AlpacaProperties(
                        new AlpacaProperties.OAuth(
                                "test-client",
                                "test-secret",
                                "https://accounts.example.com/callback",
                                "https://alpaca.example.com/authorize",
                                "https://alpaca.example.com/token",
                                600),
                        "https://alpaca.example.com",
                        Duration.ofMinutes(5)),
                authorizationStateRepository,
                alpacaConnectionRepository,
                tradingAccountProjectionRepository,
                currentUserService,
                secretStore,
                accountReconciliationService,
                alpacaTradingClient);
    }

    private OAuthAuthorizationState validState() {
        OAuthAuthorizationState state = new OAuthAuthorizationState();
        state.setStateId(UUID.randomUUID());
        state.setUserId(UUID.randomUUID());
        state.setEnvironment(AlpacaConnection.Environment.PAPER);
        state.setExpiresAt(Instant.now().plusSeconds(60));
        return state;
    }
}
