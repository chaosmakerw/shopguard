package com.codesprint.lesson0831.product;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.codesprint.lesson0831.order.OrderTools;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolDispatcherLogRedactionTest {

    @Test
    void toolFailureDoesNotLogSensitiveArgumentOrExceptionMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(ToolDispatcher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            ProductTools productTools = mock(ProductTools.class);
            when(productTools.searchProducts(any(ProductSearchRequest.class)))
                    .thenThrow(new IllegalStateException("api-key=TOOL_KEY_MARKER"));
            ToolDispatcher dispatcher = new ToolDispatcher(
                    new ToolArgumentValidator(), new ToolArgumentConverter(),
                    productTools, mock(OrderTools.class));

            ToolResult<?> result = dispatcher.dispatch("search_products",
                    Map.of("keyword", "PRIVATE_QUERY_MARKER", "page", 1,
                            "size", 1, "sort", "newest"));

            assertFalse(result.success());
            assertEquals("INTERNAL_ERROR", result.code());
            assertFalse(result.message().contains("TOOL_KEY_MARKER"));
            StringBuilder captured = new StringBuilder();
            for (ILoggingEvent event : appender.list) {
                captured.append(event.getFormattedMessage()).append('\n');
                if (event.getThrowableProxy() != null) {
                    captured.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
                }
            }
            assertFalse(captured.toString().contains("TOOL_KEY_MARKER"));
            assertFalse(captured.toString().contains("PRIVATE_QUERY_MARKER"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void successfulToolLogContainsRequestIdAndResultFields() {
        Logger logger =
                (Logger) LoggerFactory.getLogger(
                        ToolDispatcher.class
                );

        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        MDC.put(
                "requestId",
                "req-tool-001"
        );

        try {
            OrderTools orderTools =
                    mock(OrderTools.class);

            when(orderTools.listMyOrders())
                    .thenReturn(
                            ToolResult.success(
                                    List.of()
                            )
                    );

            ToolDispatcher dispatcher =
                    new ToolDispatcher(
                            new ToolArgumentValidator(),
                            new ToolArgumentConverter(),
                            mock(ProductTools.class),
                            orderTools
                    );

            ToolResult<?> result =
                    dispatcher.dispatch(
                            "list_my_orders",
                            Map.of()
                    );

            assertTrue(result.success());
            assertEquals("OK", result.code());

            StringBuilder captured =
                    new StringBuilder();

            for (ILoggingEvent event : appender.list) {
                captured.append(
                        event.getFormattedMessage()
                ).append('\n');
            }

            String logs = captured.toString();

            assertTrue(
                    logs.contains(
                            "requestId=req-tool-001"
                    )
            );
            assertTrue(
                    logs.contains(
                            "toolName=list_my_orders"
                    )
            );
            assertTrue(
                    logs.contains(
                            "resultCode=OK"
                    )
            );
            assertTrue(
                    logs.matches(
                            "(?s).*latencyMs=\\d+.*"
                    )
            );
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
