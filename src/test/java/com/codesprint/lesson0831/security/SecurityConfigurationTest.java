package com.codesprint.lesson0831.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration =
            new SecurityConfiguration();

    @Test
    void shouldConvertRolesClaimToRoleAuthorities() {
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1001")
                .claim(
                        "roles",
                        List.of("USER", "ADMIN")
                )
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        Authentication authentication =
                configuration
                        .jwtAuthenticationConverter()
                        .convert(jwt);

        Set<String> authorities =
                authentication.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toSet());

        assertEquals(
                Set.of("ROLE_USER", "ROLE_ADMIN"),
                authorities
        );
    }

    @Test
    void shouldReturnNoAuthoritiesWhenRolesClaimIsMissing() {
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        Authentication authentication =
                configuration
                        .jwtAuthenticationConverter()
                        .convert(jwt);

        assertEquals(
                Set.of(),
                authentication.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toSet())
        );
    }
}
