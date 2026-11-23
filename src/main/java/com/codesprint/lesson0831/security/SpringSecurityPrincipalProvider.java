package com.codesprint.lesson0831.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("prod & !platform")
public class SpringSecurityPrincipalProvider
        implements AuthenticatedPrincipalProvider {

    @Override
    public Optional<AuthenticatedPrincipal> current() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Long userId = parseUserId(
                token.getToken().getSubject()
        );

        if (userId == null) {
            return Optional.empty();
        }

        boolean admin = token.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        return Optional.of(
                new AuthenticatedPrincipal(
                        userId,
                        admin
                )
        );
    }

    private Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }

        try {
            long userId = Long.parseLong(subject);

            if (userId <= 0) {
                return null;
            }

            return userId;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
