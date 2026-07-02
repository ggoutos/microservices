package com.ggoutos.accounts.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Accounts KeycloakRoleConverter Tests")
class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Test
    @DisplayName("Should map realm roles to Spring Security authorities")
    void convert_shouldMapRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("ACCOUNTS", "CARDS")))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ACCOUNTS", "ROLE_CARDS");
    }

    @Test
    @DisplayName("Should return empty authorities when realm roles are missing")
    void convert_shouldReturnEmptyAuthorities_whenRealmRolesMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }
}
