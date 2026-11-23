package com.codesprint.lesson0831.order;

import com.codesprint.lesson0831.product.*;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDetailToolTest {
    private ToolDispatcher dispatcher(Long user, OrderService service) {
        return new ToolDispatcher(new ToolArgumentValidator(), new ToolArgumentConverter(), mock(ProductTools.class),
                new OrderTools(new FakeCurrentUserContext(user), service));
    }

    @Test void preservesFullLongPrecisionAndUsesAuthenticatedIdentity() {
        OrderService service=mock(OrderService.class);
        var order=new OrderSummary(Long.MAX_VALUE,1001L,"已关闭订单","CANCELLED");
        when(service.findMyOrder(1001L,Long.MAX_VALUE)).thenReturn(Optional.of(order));
        var result=dispatcher(1001L,service).dispatch("get_my_order",Map.of("orderId",Long.toString(Long.MAX_VALUE)));
        assertTrue(result.success());assertEquals(order,result.data());
        verify(service).findMyOrder(1001L,Long.MAX_VALUE);
    }

    @Test void rejectsInvalidAndImpreciseIdsBeforeService() {
        OrderService service=mock(OrderService.class);var dispatcher=dispatcher(1001L,service);
        for(Object id:List.of("0","-1","01","1.5","1e2"," 1 ","9223372036854775808",1L,1.2,true)) {
            var result=dispatcher.dispatch("get_my_order",Map.of("orderId",id));
            assertEquals("INVALID_ARGUMENT",result.code(),String.valueOf(id));
        }
        assertEquals("MISSING_ARGUMENT",dispatcher.dispatch("get_my_order",Map.of()).code());
        assertEquals("MISSING_ARGUMENT",dispatcher.dispatch("get_my_order",null).code());
        verifyNoInteractions(service);
    }

    @Test void rejectsSuppliedIdentityAndAnonymousUserBeforeService() {
        OrderService service=mock(OrderService.class);
        assertEquals("UNKNOWN_ARGUMENT",dispatcher(1001L,service).dispatch("get_my_order",Map.of("orderId","1","buyerId",1002L)).code());
        assertEquals("UNAUTHORIZED",dispatcher(null,service).dispatch("get_my_order",Map.of("orderId","1")).code());
        verifyNoInteractions(service);
    }

    @Test void absentOrOtherOwnersOrderHasSameSafeResult() {
        OrderService service=mock(OrderService.class);
        when(service.findMyOrder(1001L,2L)).thenReturn(Optional.empty());
        var result=dispatcher(1001L,service).dispatch("get_my_order",Map.of("orderId","2"));
        assertEquals("ORDER_NOT_FOUND",result.code());assertNull(result.data());
    }

    @Test void databaseFailureIsSafeAndNextRequestCanRecover() {
        OrderService service=mock(OrderService.class);
        var order=new OrderSummary(1L,1001L,"订单","PAID");
        when(service.findMyOrder(1001L,1L)).thenThrow(new IllegalStateException("secret database detail")).thenReturn(Optional.of(order));
        var dispatcher=dispatcher(1001L,service);var failed=dispatcher.dispatch("get_my_order",Map.of("orderId","1"));
        assertEquals("INTERNAL_ERROR",failed.code());assertFalse(failed.message().contains("secret"));
        assertEquals(order,dispatcher.dispatch("get_my_order",Map.of("orderId","1")).data());
    }
}
