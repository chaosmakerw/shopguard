package com.codesprint.lesson0831.security;

import java.util.Optional;

@FunctionalInterface
public interface AuthenticatedPrincipalProvider {

    Optional<AuthenticatedPrincipal> current();
}