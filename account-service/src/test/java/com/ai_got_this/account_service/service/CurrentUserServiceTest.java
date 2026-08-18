package com.ai_got_this.account_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.domain.User;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import com.ai_got_this.account_service.repository.UserRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AlpacaConnectionRepository alpacaConnectionRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOrProvisionCurrentUserRejectsMissingAuthentication() {
        CurrentUserService service = service();

        assertThatThrownBy(service::getOrProvisionCurrentUser)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("UNAUTHENTICATED");
                });
    }

    @Test
    void getOrProvisionCurrentUserRejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("principal", "credentials"));

        assertThatThrownBy(() -> service().getOrProvisionCurrentUser())
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code).isEqualTo("UNAUTHENTICATED");
                });
    }

    @Test
    void getOrProvisionCurrentUserCreatesUserFromJwtClaimsWhenUnknown() {
        Jwt jwt = jwt("keycloak-subject", "trader@example.com", "trader");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt, "credentials"));
        when(userRepository.findByExternalSubject("keycloak-subject")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service().getOrProvisionCurrentUser();

        assertThat(user.getUserId()).isNotNull();
        assertThat(user.getExternalSubject()).isEqualTo("keycloak-subject");
        assertThat(user.getEmail()).isEqualTo("trader@example.com");
        assertThat(user.getDisplayName()).isEqualTo("trader");
        assertThat(user.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        verify(userRepository).save(user);
    }

    @Test
    void updateDisplayNamePersistsTheCurrentUser() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setExternalSubject("keycloak-subject");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt("keycloak-subject", null, null), "credentials"));
        when(userRepository.findByExternalSubject("keycloak-subject")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User updatedUser = service().updateDisplayName("Ada Trader");

        assertThat(updatedUser.getDisplayName()).isEqualTo("Ada Trader");
        verify(userRepository).save(user);
    }

    private CurrentUserService service() {
        return new CurrentUserService(
                userRepository,
                alpacaConnectionRepository,
                watchlistRepository);
    }

    private Jwt jwt(String subject, String email, String preferredUsername) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        if (email != null) {
            claims.put("email", email);
        }
        if (preferredUsername != null) {
            claims.put("preferred_username", preferredUsername);
        }

        return new Jwt(
                "token-value",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                claims);
    }
}
