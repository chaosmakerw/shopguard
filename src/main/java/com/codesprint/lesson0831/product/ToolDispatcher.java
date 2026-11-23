package com.codesprint.lesson0831.product;

import com.codesprint.lesson0831.order.OrderTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.Set;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;

import java.util.Objects;
public class ToolDispatcher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ToolDispatcher.class
            );

    private static final Set<String> KNOWN_TOOLS =
            Set.of(
                    "search_products",
                    "get_product_detail",
                    "list_my_orders",
                    "get_my_order"
            );

    private final ToolArgumentValidator validator;
    private final ToolArgumentConverter converter;
    private final ProductTools productTools;
    private final OrderTools orderTools;
    private final AgentOperationMetrics metrics;
    public ToolDispatcher(
            ToolArgumentValidator validator,
            ToolArgumentConverter converter,
            ProductTools productTools,
            OrderTools orderTools
    ) {
        this(
                validator,
                converter,
                productTools,
                orderTools,
                AgentOperationMetrics.noop()
        );
    }

    public ToolDispatcher(
            ToolArgumentValidator validator,
            ToolArgumentConverter converter,
            ProductTools productTools,
            OrderTools orderTools,
            AgentOperationMetrics metrics
    ) {
        this.validator = validator;
        this.converter = converter;
        this.productTools = productTools;
        this.orderTools = orderTools;
        this.metrics = Objects.requireNonNull(
                metrics,
                "指标组件不能为空"
        );
    }

    public ToolResult<?> dispatch(
            String toolName,
            Map<String, Object> arguments
    ) {
        long startNanos = System.nanoTime();
        try {
            ToolResult<Void> validation =
                    validator.validate(
                            toolName,
                            arguments
                    );

            if (!validation.success()) {
                return recordResult(
                        toolName,
                        startNanos,
                        validation
                );
            }

            if ("get_my_order".equals(toolName)) {
                return recordResult(toolName, startNanos, orderTools.getMyOrder(converter.toOrderId(arguments)));
            }

            if ("list_my_orders".equals(toolName)) {
                return recordResult(
                        toolName,
                        startNanos,
                        orderTools.listMyOrders()
                );
            }

            if ("search_products".equals(toolName)) {
                ProductSearchRequest request =
                        converter.toSearchRequest(arguments);

                return recordResult(
                        toolName,
                        startNanos,
                        productTools.searchProducts(request)
                );
            }

            if ("get_product_detail".equals(toolName)) {
                long productId =
                        converter.toProductId(arguments);

                return recordResult(
                        toolName,
                        startNanos,
                        productTools.getProductDetail(
                                productId
                        )
                );
            }

            return recordResult(
                    toolName,
                    startNanos,
                    ToolResult.failure(
                            "UNKNOWN_TOOL",
                            "未知工具: " + toolName
                    )
            );
        } catch (RuntimeException exception) {
            long elapsedNanos =
                    System.nanoTime() - startNanos;

            long latencyMs =
                    elapsedNanos / 1_000_000;

            metrics.record(
                    "tool",
                    "INTERNAL_ERROR",
                    elapsedNanos
            );

            LOGGER.error(
                    "tool execution requestId={} toolName={} latencyMs={} resultCode=INTERNAL_ERROR exceptionType={}",
                    currentRequestId(),
                    safeToolName(toolName),
                    latencyMs,
                    exception.getClass().getName()
            );
            return ToolResult.failure(
                    "INTERNAL_ERROR",
                    "工具执行失败，请稍后重试"
            );
        }
    }
    private ToolResult<?> recordResult(
            String toolName,
            long startNanos,
            ToolResult<?> result
    ) {
        long elapsedNanos =
                System.nanoTime() - startNanos;

        long latencyMs =
                elapsedNanos / 1_000_000;

        metrics.record(
                "tool",
                result.code(),
                elapsedNanos
        );
        LOGGER.info(
                "tool execution requestId={} toolName={} latencyMs={} resultCode={}",
                currentRequestId(),
                safeToolName(toolName),
                latencyMs,
                result.code()
        );

        return result;
    }

    private String currentRequestId() {
        String requestId = MDC.get("requestId");

        if (requestId == null || requestId.isBlank()) {
            return "UNAVAILABLE";
        }

        return requestId;
    }

    private String safeToolName(String toolName) {
        if (KNOWN_TOOLS.contains(toolName)) {
            return toolName;
        }

        return "UNKNOWN";
    }
}
