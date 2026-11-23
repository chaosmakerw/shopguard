package com.codesprint.lesson0831.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSecurityPrincipalProviderTest {

    private final SpringSecurityPrincipalProvider provider =
            new SpringSecurityPrincipalProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadUserIdFromVerifiedJwtSubject() {
        Jwt jwt = jwt("1001");

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of()
                )
        );

        var principal = provider.current();

        assertTrue(principal.isPresent());
        assertEquals(
                Long.valueOf(1001L),
                principal.get().userId()
        );
        assertFalse(principal.get().admin());
    }

    @Test
    void shouldReadAdminAuthority() {
        Jwt jwt = jwt("9001");

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_ADMIN"
                                )
                        )
                )
        );

        var principal = provider.current();

        assertTrue(principal.isPresent());
        assertTrue(principal.get().admin());
    }

    @Test
    void shouldIgnoreInvalidSubject() {
        Jwt jwt = jwt("not-a-number");

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of()
                )
        );

        assertTrue(provider.current().isEmpty());
    }

    @Test
    void shouldIgnoreNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "password"
                )
        );

        assertTrue(provider.current().isEmpty());
    }

    private Jwt jwt(String subject) {
        Instant now = Instant.now();

        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
