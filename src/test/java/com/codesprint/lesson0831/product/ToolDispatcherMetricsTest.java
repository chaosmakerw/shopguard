package com.codesprint.lesson0831.product;

import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import com.codesprint.lesson0831.order.OrderTools;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolDispatcherMetricsTest {

    @Test
    void recordsBoundedMetricsForSuccessAndInternalFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOperationMetrics metrics = new AgentOperationMetrics(registry);
        ProductTools productTools = mock(ProductTools.class);
        OrderTools orderTools = mock(OrderTools.class);

        when(orderTools.listMyOrders())
                .thenReturn(ToolResult.success(List.of()));
        when(productTools.searchProducts(any(ProductSearchRequest.class)))
                .thenThrow(new IllegalStateException("secret-database-address"));

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                productTools,
                orderTools,
                metrics
        );

        ToolResult<?> success = dispatcher.dispatch(
                "list_my_orders",
                Map.of()
        );
        ToolResult<?> failure = dispatcher.dispatch(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 1,
                        "size", 10,
                        "sort", "newest"
                )
        );

        assertEquals("OK", success.code());
        assertEquals("INTERNAL_ERROR", failure.code());

        Timer successTimer = registry.get(AgentOperationMetrics.METRIC_NAME)
                .tag("stage", "tool")
                .tag("resultCode", "OK")
                .timer();
        Timer failureTimer = registry.get(AgentOperationMetrics.METRIC_NAME)
                .tag("stage", "tool")
                .tag("resultCode", "INTERNAL_ERROR")
                .timer();

        assertEquals(1L, successTimer.count());
        assertEquals(1L, failureTimer.count());
        assertFalse(successTimer.totalTime(TimeUnit.NANOSECONDS) < 0);
        assertFalse(failureTimer.totalTime(TimeUnit.NANOSECONDS) < 0);
        assertEquals(2, registry.find(AgentOperationMetrics.METRIC_NAME)
                .tag("stage", "tool")
                .timers().size());
    }
}
