package com.ai_got_this.account_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.service.AccountReconciliationService;
import com.ai_got_this.account_service.service.AlpacaOAuthService;
import com.ai_got_this.account_service.service.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class IdentityAndOAuthControllerTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AlpacaOAuthService alpacaOAuthService;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private AccountReconciliationService accountReconciliationService;

    @Test
    void meControllerMapsTheCurrentUserAndProfileUpdate() {
        User user = user();
        when(currentUserService.getOrProvisionCurrentUser()).thenReturn(user);
        when(currentUserService.updateDisplayName("Ada Trader")).thenReturn(user);
        user.setDisplayName("Ada Trader");

        MeController.Me currentUser = meController().getCurrentUser();
        MeController.Me updatedUser = meController().updateProfile(new MeController.Patch("Ada Trader"));

        assertThat(currentUser.userId()).isEqualTo(user.getUserId());
        assertThat(currentUser.email()).isEqualTo(user.getEmail());
        assertThat(updatedUser.displayName()).isEqualTo("Ada Trader");
        verify(currentUserService).updateDisplayName("Ada Trader");
    }

    @Test
    void oauthControllerReturnsAuthorizationAndRedirectsSuccessfulCallbacks() {
        UUID connectionId = UUID.randomUUID();
        AlpacaOAuthService.Authorization authorization = new AlpacaOAuthService.Authorization(
                "https://alpaca.example.com/authorize",
                Instant.now().plusSeconds(600));
        when(alpacaOAuthService.createAuthorizationUrl()).thenReturn(authorization);
        when(alpacaOAuthService.consumeCallback("code", "state")).thenReturn(connectionId);

        AlpacaOAuthService.Authorization returnedAuthorization = oauthController().beginAuthorization();
        ResponseEntity<Void> callbackResponse = oauthController().handleCallback("code", "state");

        assertThat(returnedAuthorization).isSameAs(authorization);
        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(callbackResponse.getHeaders().getLocation())
                .hasToString("/settings/connections/success?connectionId=" + connectionId);
    }

    @Test
    void oauthControllerListsOnlyTheCurrentUsersConnections() {
        User user = user();
        AlpacaConnection connection = connection();
        when(currentUserService.getOrProvisionCurrentUser()).thenReturn(user);
        when(alpacaConnectionRepository.findByUserId(user.getUserId())).thenReturn(List.of(connection));

        List<AlpacaConnection> connections = oauthController().listConnections();

        assertThat(connections).containsExactly(connection);
    }

    @Test
    void oauthControllerDelegatesOwnedConnectionReconciliationAndDisconnection() {
        AlpacaConnection connection = connection();
        when(currentUserService.requireOwnedConnection(connection.getConnectionId())).thenReturn(connection);
        when(accountReconciliationService.reconcileConnection(connection)).thenReturn(null);

        oauthController().reconcile(connection.getConnectionId());
        oauthController().disconnect(connection.getConnectionId());

        verify(accountReconciliationService).reconcileConnection(connection);
        verify(alpacaOAuthService).disconnectConnection(connection);
    }

    private MeController meController() {
        return new MeController(currentUserService);
    }

    private AlpacaOAuthController oauthController() {
        return new AlpacaOAuthController(
                alpacaOAuthService,
                currentUserService,
                alpacaConnectionRepository,
                accountReconciliationService);
    }

    private User user() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setEmail("trader@example.com");
        user.setDisplayName("trader");
        return user;
    }

    private AlpacaConnection connection() {
        AlpacaConnection connection = new AlpacaConnection();
        connection.setConnectionId(UUID.randomUUID());
        connection.setAlpacaAccountId(UUID.randomUUID());
        return connection;
    }
}
