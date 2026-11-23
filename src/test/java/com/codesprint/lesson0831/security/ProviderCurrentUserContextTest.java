package com.codesprint.lesson0831.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCurrentUserContextTest {

    @Test
    void shouldExposeAuthenticatedUser() {
        AuthenticatedPrincipalProvider provider =
                () -> Optional.of(
                        new AuthenticatedPrincipal(
                                1001L,
                                false
                        )
                );

        ProviderCurrentUserContext context =
                new ProviderCurrentUserContext(provider);

        assertEquals(
                Long.valueOf(1001L),
                context.getUserId()
        );

        assertFalse(context.isAdmin());
    }

    @Test
    void shouldExposeAuthenticatedAdmin() {
        AuthenticatedPrincipalProvider provider =
                () -> Optional.of(
                        new AuthenticatedPrincipal(
                                9001L,
                                true
                        )
                );

        ProviderCurrentUserContext context =
                new ProviderCurrentUserContext(provider);

        assertEquals(
                Long.valueOf(9001L),
                context.getUserId()
        );

        assertTrue(context.isAdmin());
    }

    @Test
    void shouldReturnUnauthenticatedState() {
        AuthenticatedPrincipalProvider provider =
                Optional::empty;

        ProviderCurrentUserContext context =
                new ProviderCurrentUserContext(provider);

        assertNull(context.getUserId());
        assertFalse(context.isAdmin());
    }

    @Test
    void shouldRejectNullProvider() {
        assertThrows(
                NullPointerException.class,
                () -> new ProviderCurrentUserContext(null)
        );
    }

    @Test
    void shouldRejectInvalidPrincipalId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(
                        0L,
                        false
                )
        );
    }
}
