package com.codesprint.lesson0831.product;

import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ToolDispatcherDiagnosticsTest {

    @Test
    void technicalExceptionShouldBeLoggedWithoutSensitiveDetail(
            CapturedOutput output
    ) {
        ToolArgumentValidator validator =
                mock(ToolArgumentValidator.class);

        ToolArgumentConverter converter =
                mock(ToolArgumentConverter.class);

        ProductTools productTools =
                mock(ProductTools.class);

        OrderTools orderTools =
                mock(OrderTools.class);

        Map<String, Object> arguments =
                Map.of("productId", 2);

        when(validator.validate(
                "get_product_detail",
                arguments
        )).thenReturn(
                ToolResult.success(null)
        );

        when(converter.toProductId(arguments))
                .thenThrow(
                        new IllegalStateException(
                                "jdbc:mysql://internal-db password=secret"
                        )
                );

        ToolDispatcher dispatcher =
                new ToolDispatcher(
                        validator,
                        converter,
                        productTools,
                        orderTools
                );

        ToolResult<?> result =
                dispatcher.dispatch(
                        "get_product_detail",
                        arguments
                );

        assertEquals(
                ToolResult.failure(
                        "INTERNAL_ERROR",
                        "工具执行失败，请稍后重试"
                ),
                result
        );

        String logs = output.getAll();

        assertTrue(
                logs.contains(
                        "toolName=get_product_detail"
                )
        );

        assertTrue(
                logs.contains(
                        "exceptionType=java.lang.IllegalStateException"
                )
        );

        assertFalse(logs.contains("internal-db"));
        assertFalse(logs.contains("password"));
        assertFalse(logs.contains("secret"));

        verify(validator).validate(
                "get_product_detail",
                arguments
        );

        verify(converter)
                .toProductId(arguments);

        verifyNoInteractions(
                productTools,
                orderTools
        );
    }

    @Test
    void mysqlFailureShouldReturnSafeResultAndSafeLog(
            CapturedOutput output
    ) {
        ProductService productService =
                mock(ProductService.class);

        OrderTools orderTools =
                mock(OrderTools.class);

        when(productService.getDetail(2L))
                .thenThrow(
                        new IllegalStateException(
                                "jdbc:mysql://internal-db:3306/"
                                        + "shop password=mysql-secret"
                        )
                );

        ToolDispatcher dispatcher =
                new ToolDispatcher(
                        new ToolArgumentValidator(),
                        new ToolArgumentConverter(),
                        new ProductTools(productService),
                        orderTools
                );

        ToolResult<?> result =
                dispatcher.dispatch(
                        "get_product_detail",
                        Map.of("productId", 2)
                );

        assertFalse(result.success());
        assertEquals(
                "INTERNAL_ERROR",
                result.code()
        );
        assertEquals(
                "工具执行失败，请稍后重试",
                result.message()
        );
        assertEquals(null, result.data());

        String logs = output.getAll();

        assertTrue(
                logs.contains(
                        "toolName=get_product_detail"
                )
        );
        assertTrue(
                logs.contains(
                        "exceptionType=java.lang.IllegalStateException"
                )
        );

        assertFalse(logs.contains("internal-db"));
        assertFalse(logs.contains("3306"));
        assertFalse(logs.contains("password"));
        assertFalse(logs.contains("mysql-secret"));

        verify(productService)
                .getDetail(2L);

        verifyNoInteractions(orderTools);
    }

    @Test
    void redisFailureShouldReturnSafeResultAndSafeLog(
            CapturedOutput output
    ) {
        OrderService orderService =
                mock(OrderService.class);

        ProductTools productTools =
                mock(ProductTools.class);

        when(orderService.listMyOrders(1001L))
                .thenThrow(
                        new IllegalStateException(
                                "redis://internal-cache:6379 "
                                        + "password=redis-secret"
                        )
                );

        OrderTools orderTools =
                new OrderTools(
                        new FakeCurrentUserContext(1001L),
                        orderService
                );

        ToolDispatcher dispatcher =
                new ToolDispatcher(
                        new ToolArgumentValidator(),
                        new ToolArgumentConverter(),
                        productTools,
                        orderTools
                );

        ToolResult<?> result =
                dispatcher.dispatch(
                        "list_my_orders",
                        Map.of()
                );

        assertFalse(result.success());
        assertEquals(
                "INTERNAL_ERROR",
                result.code()
        );
        assertEquals(
                "工具执行失败，请稍后重试",
                result.message()
        );
        assertEquals(null, result.data());

        String logs = output.getAll();

        assertTrue(
                logs.contains(
                        "toolName=list_my_orders"
                )
        );
        assertTrue(
                logs.contains(
                        "exceptionType=java.lang.IllegalStateException"
                )
        );

        assertFalse(logs.contains("internal-cache"));
        assertFalse(logs.contains("6379"));
        assertFalse(logs.contains("password"));
        assertFalse(logs.contains("redis-secret"));

        verify(orderService)
                .listMyOrders(1001L);

        verifyNoInteractions(productTools);
    }
}