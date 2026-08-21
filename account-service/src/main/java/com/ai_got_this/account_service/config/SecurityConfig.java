package com.ai_got_this.account_service.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            Converter<Jwt, JwtAuthenticationToken> keycloakJwtAuthenticationConverter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers("/api/v1/alpaca/oauth/callback")
                                        .permitAll()
                                        .requestMatchers("/internal/v1/order-execution/active-connection-credentials")
                                        .hasRole("ORDER_EXECUTION_SERVICE")
                                        .requestMatchers("/internal/**")
                                        .hasAnyRole("RISK_SERVICE", "STRATEGY_SERVICE", "ORDER_EXECUTION_SERVICE")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
                .build();
    }

    /**
     * Keycloak puts realm roles in {@code realm_access.roles}, whereas Spring's
     * default converter recognizes only OAuth scopes. Preserve scope authorities
     * and turn realm roles into Spring's {@code ROLE_*} authorities so the
     * {@code hasAnyRole(...)} rules above can authorize service accounts.
     */
    @Bean
    Converter<Jwt, JwtAuthenticationToken> keycloakJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();

        return jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopeAuthorities.convert(jwt));
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Object roles = realmAccess == null ? null : realmAccess.get("roles");

            if (roles instanceof Collection<?> roleNames) {
                roleNames.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(role -> !role.isBlank())
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(authorities::add);
            }

            return new JwtAuthenticationToken(jwt, authorities);
        };
    }
}
