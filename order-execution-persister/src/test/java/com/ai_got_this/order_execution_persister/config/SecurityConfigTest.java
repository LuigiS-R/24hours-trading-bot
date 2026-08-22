package com.ai_got_this.order_execution_persister.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityConfigTest {
    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void mapsTheAccountServiceRealmRoleForTheInternalReadApi() {
        Jwt jwt = Jwt.withTokenValue("service-token")
                .header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of("ACCOUNT_SERVICE")))
                .build();

        JwtAuthenticationToken authentication = securityConfig
                .keycloakJwtAuthenticationConverter()
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_ACCOUNT_SERVICE");
    }
}
