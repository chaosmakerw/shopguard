package com.codesprint.lesson0831.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class AgentOperationMetrics {

    public static final String METRIC_NAME =
            "agent.operation.duration";

    private static final Set<String> ALLOWED_STAGES =
            Set.of(
                    "tool",
                    "retrieval",
                    "model"
            );

    private static final Set<String> ALLOWED_RESULT_CODES =
            Set.of(
                    "OK",
                    "SUCCESS",
                    "MISSING_ARGUMENT",
                    "UNKNOWN_ARGUMENT",
                    "INVALID_ARGUMENT",
                    "UNKNOWN_TOOL",
                    "NOT_FOUND",
                    "UNAUTHORIZED",
                    "INTERNAL_ERROR",
                    "NO_RESULT",
                    "LOW_RELEVANCE",
                    "VECTOR_STORE_UNAVAILABLE",
                    "MODEL_UNAVAILABLE"
            );

    private final MeterRegistry registry;
    private final boolean enabled;

    @Autowired
    public AgentOperationMetrics(
            MeterRegistry registry
    ) {
        this.registry = Objects.requireNonNull(
                registry,
                "MeterRegistry不能为空"
        );
        this.enabled = true;
    }

    private AgentOperationMetrics() {
        this.registry = null;
        this.enabled = false;
    }

    public static AgentOperationMetrics noop() {
        return new AgentOperationMetrics();
    }

    public void record(
            String stage,
            String resultCode,
            long elapsedNanos
    ) {
        if (!ALLOWED_STAGES.contains(stage)) {
            throw new IllegalArgumentException(
                    "未知指标阶段"
            );
        }

        if (elapsedNanos < 0) {
            throw new IllegalArgumentException(
                    "耗时不能为负数"
            );
        }

        String safeResultCode =
                ALLOWED_RESULT_CODES.contains(
                        resultCode
                )
                        ? resultCode
                        : "UNKNOWN";

        if (!enabled) {
            return;
        }

        Timer.builder(METRIC_NAME)
                .description(
                        "Agent各处理阶段耗时"
                )
                .tag("stage", stage)
                .tag(
                        "resultCode",
                        safeResultCode
                )
                .register(registry)
                .record(
                        elapsedNanos,
                        TimeUnit.NANOSECONDS
                );
    }
}
