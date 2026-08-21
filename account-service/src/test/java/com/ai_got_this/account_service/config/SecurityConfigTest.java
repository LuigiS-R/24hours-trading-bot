package com.ai_got_this.account_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void mapsKeycloakRealmRolesToSpringRoleAuthoritiesAndPreservesScopes() {
        Jwt jwt = Jwt.withTokenValue("service-token")
                .header("alg", "RS256")
                .claim("scope", "portfolio:read")
                .claim("realm_access", Map.of("roles", List.of("STRATEGY_SERVICE", "offline_access")))
                .build();

        JwtAuthenticationToken authentication = securityConfig
                .keycloakJwtAuthenticationConverter()
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "SCOPE_portfolio:read",
                        "ROLE_STRATEGY_SERVICE",
                        "ROLE_offline_access");
    }

    @Test
    void permitsTokensWithoutRealmRolesToUseTheirStandardScopes() {
        Jwt jwt = Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .claim("scope", "openid profile")
                .build();

        JwtAuthenticationToken authentication = securityConfig
                .keycloakJwtAuthenticationConverter()
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile");
    }
}
