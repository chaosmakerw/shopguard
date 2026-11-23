package com.codesprint.lesson0831.security;

import java.util.Objects;

public class ProviderCurrentUserContext
        implements CurrentUserContext {

    private final AuthenticatedPrincipalProvider provider;

    public ProviderCurrentUserContext(
            AuthenticatedPrincipalProvider provider
    ) {
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public Long getUserId() {
        return provider.current()
                .map(AuthenticatedPrincipal::userId)
                .orElse(null);
    }

    @Override
    public boolean isAdmin() {
        return provider.current()
                .map(AuthenticatedPrincipal::admin)
                .orElse(false);
    }
}