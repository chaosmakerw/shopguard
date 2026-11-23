package com.codesprint.lesson0831.order;

import com.codesprint.lesson0831.common.BizException;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderToolsTest {

    @Test
    void shouldReturnOnlyCurrentUsersOrders() {
        OrderTools tools = new OrderTools(
                new FakeCurrentUserContext(1001L),
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
    void shouldReturnEmptyListWhenUserHasNoOrders() {
        OrderTools tools = new OrderTools(
                new FakeCurrentUserContext(9999L),
                new FakeOrderService()
        );

        var result = tools.listMyOrders();

        assertTrue(result.success());
        assertNotNull(result.data());
        assertTrue(result.data().isEmpty());
    }

    @Test
    void shouldRejectUnauthenticatedUserWithoutCallingService() {
        OrderService orderService =
                mock(OrderService.class);

        OrderTools tools = new OrderTools(
                new FakeCurrentUserContext(null),
                orderService
        );

        var result = tools.listMyOrders();

        assertFalse(result.success());
        assertEquals(
                "UNAUTHORIZED",
                result.code()
        );
        assertEquals(
                "请先登录",
                result.message()
        );
        assertNull(result.data());

        verifyNoInteractions(orderService);
    }

    @Test
    void shouldPreserveBusinessExceptionCode() {
        OrderService orderService =
                mock(OrderService.class);

        OrderTools tools = new OrderTools(
                new FakeCurrentUserContext(1001L),
                orderService
        );

        when(orderService.listMyOrders(1001L))
                .thenThrow(
                        new BizException(
                                "ORDER_FORBIDDEN",
                                "无权查看订单"
                        )
                );

        var result = tools.listMyOrders();

        assertFalse(result.success());
        assertEquals(
                "ORDER_FORBIDDEN",
                result.code()
        );
        assertEquals(
                "无权查看订单",
                result.message()
        );
        assertNull(result.data());

        verify(orderService)
                .listMyOrders(1001L);
    }

    @Test
    void technicalExceptionShouldContinueToDispatcher() {
        OrderService orderService =
                mock(OrderService.class);

        OrderTools tools = new OrderTools(
                new FakeCurrentUserContext(1001L),
                orderService
        );

        when(orderService.listMyOrders(1001L))
                .thenThrow(
                        new IllegalStateException(
                                "redis://internal-cache password=secret"
                        )
                );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        tools::listMyOrders
                );

        assertEquals(
                "redis://internal-cache password=secret",
                exception.getMessage()
        );

        verify(orderService)
                .listMyOrders(1001L);
    }
}