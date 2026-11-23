package com.codesprint.lesson0831.order;

import com.codesprint.lesson0831.product.*;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderToolDispatcherTest {

    @Test
    void shouldDispatchOrdersForCurrentUser() {
        OrderTools orderTools = new OrderTools(
                new FakeCurrentUserContext(1001L),
                new FakeOrderService()
        );

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                mock(ProductTools.class),
                orderTools
        );

        var result = dispatcher.dispatch(
                "list_my_orders",
                Map.of()
        );

        assertTrue(result.success());
        assertEquals(
                List.of(new OrderSummary(
                        1L, 1001L, "Java编程思想", "PAID"
                )),
                result.data()
        );
    }

    @Test
    void shouldRejectIdentityFieldsBeforeCallingTools() {
        OrderTools orderTools = mock(OrderTools.class);
        ProductTools productTools = mock(ProductTools.class);

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                productTools,
                orderTools
        );

        for (String field : List.of(
                "userId", "buyerId", "sellerId"
        )) {
            var result = dispatcher.dispatch(
                    "list_my_orders",
                    Map.of(field, 1002L)
            );

            assertFalse(result.success(), field);
            assertEquals(
                    "UNKNOWN_ARGUMENT",
                    result.code(),
                    field
            );
        }

        verifyNoInteractions(orderTools, productTools);
    }

    @Test
    void shouldPreserveUnauthenticatedResult() {
        OrderService orderService = mock(OrderService.class);

        OrderTools orderTools = new OrderTools(
                new FakeCurrentUserContext(null),
                orderService
        );

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                mock(ProductTools.class),
                orderTools
        );

        var result = dispatcher.dispatch(
                "list_my_orders",
                Map.of()
        );

        assertFalse(result.success());
        assertEquals("UNAUTHORIZED", result.code());
        assertNull(result.data());
        verifyNoInteractions(orderService);
    }
    @Test
    void shouldRejectForgedRolesBeforeCallingTools() {
        OrderTools orderTools = mock(OrderTools.class);
        ProductTools productTools = mock(ProductTools.class);

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                productTools,
                orderTools
        );

        List<Map<String, Object>> forgedArguments = List.of(
                Map.<String, Object>of("role", "ADMIN"),
                Map.<String, Object>of("isAdmin", true),
                Map.<String, Object>of(
                        "userId", 1002L,
                        "role", "ADMIN"
                )
        );

        for (Map<String, Object> arguments : forgedArguments) {
            var result = dispatcher.dispatch(
                    "list_my_orders",
                    arguments
            );

            assertFalse(result.success(), arguments.toString());
            assertEquals(
                    "UNKNOWN_ARGUMENT",
                    result.code(),
                    arguments.toString()
            );
        }

        verifyNoInteractions(orderTools, productTools);
    }
    @Test
    void shouldReturnOrdersForAnotherCurrentUser() {
        OrderTools orderTools = new OrderTools(
                new FakeCurrentUserContext(1002L),
                new FakeOrderService()
        );

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                mock(ProductTools.class),
                orderTools
        );

        var result = dispatcher.dispatch(
                "list_my_orders",
                Map.of()
        );

        assertTrue(result.success());
        assertEquals(
                List.of(new OrderSummary(
                        2L, 1002L, "数据结构", "CREATED"
                )),
                result.data()
        );
    }
    @Test
    void shouldRejectForgedIdentityEvenWhenUnauthenticated() {
        OrderService orderService = mock(OrderService.class);

        OrderTools orderTools = new OrderTools(
                new FakeCurrentUserContext(null),
                orderService
        );

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                mock(ProductTools.class),
                orderTools
        );

        var result = dispatcher.dispatch(
                "list_my_orders",
                Map.of("userId", 1001L)
        );

        assertFalse(result.success());
        assertEquals("UNKNOWN_ARGUMENT", result.code());
        verifyNoInteractions(orderService);
    }
    @Test
    void shouldLimitAdminToOwnOrders() {
        FakeCurrentUserContext context =
                new FakeCurrentUserContext(1001L, true);

        assertTrue(context.isAdmin());

        OrderTools orderTools = new OrderTools(
                context,
                new FakeOrderService()
        );

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                mock(ProductTools.class),
                orderTools
        );

        var result = dispatcher.dispatch(
                "list_my_orders",
                Map.of()
        );

        assertTrue(result.success());
        assertEquals(
                List.of(new OrderSummary(
                        1L, 1001L, "Java编程思想", "PAID"
                )),
                result.data()
        );
    }
    @Test
    void shouldReturnEmptyListForAdminWithoutOwnOrders() {
        FakeCurrentUserContext context =
                new FakeCurrentUserContext(9999L, true);

        OrderTools orderTools = new OrderTools(
                context,
                new FakeOrderService()
        );

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                mock(ProductTools.class),
                orderTools
        );

        var result = dispatcher.dispatch(
                "list_my_orders",
                Map.of()
        );

        assertTrue(context.isAdmin());
        assertTrue(result.success());
        assertEquals(List.of(), result.data());
    }
}