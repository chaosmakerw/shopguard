package com.codesprint.lesson0831.security;

import com.codesprint.lesson0831.order.FakeOrderService;
import com.codesprint.lesson0831.order.OrderTools;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCurrentUserContextOrderToolsTest {

    @Test
    void shouldUseAuthenticatedUserForOrderQuery() {
        AuthenticatedPrincipalProvider provider =
                () -> Optional.of(
                        new AuthenticatedPrincipal(
                                1001L,
                                false
                        )
                );

        ProviderCurrentUserContext context =
                new ProviderCurrentUserContext(provider);

        OrderTools tools =
                new OrderTools(
                        context,
                        new FakeOrderService()
                );

        var result = tools.listMyOrders();

        assertTrue(result.success());
        assertEquals(1, result.data().size());
        assertEquals(
                Long.valueOf(1001L),
                result.data().get(0).buyerId()
        );
    }

    @Test
    void shouldReturnEmptyOrdersForAuthenticatedUserWithoutOrders() {
        AuthenticatedPrincipalProvider provider =
                () -> Optional.of(
                        new AuthenticatedPrincipal(
                                9999L,
                                false
                        )
                );

        ProviderCurrentUserContext context =
                new ProviderCurrentUserContext(provider);

        OrderTools tools =
                new OrderTools(
                        context,
                        new FakeOrderService()
                );

        var result = tools.listMyOrders();

        assertTrue(result.success());
        assertTrue(result.data().isEmpty());
    }

    @Test
    void shouldReturnUnauthorizedWhenProviderHasNoUser() {
        AuthenticatedPrincipalProvider provider =
                Optional::empty;

        ProviderCurrentUserContext context =
                new ProviderCurrentUserContext(provider);

        OrderTools tools =
                new OrderTools(
                        context,
                        new FakeOrderService()
                );

        var result = tools.listMyOrders();

        assertEquals(
                "UNAUTHORIZED",
                result.code()
        );
    }
}
