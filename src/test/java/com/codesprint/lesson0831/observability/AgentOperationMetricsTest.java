package com.codesprint.lesson0831.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentOperationMetricsTest {

    @Test
    void sameTagsAccumulateInOneTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOperationMetrics metrics = new AgentOperationMetrics(registry);

        metrics.record("model", "SUCCESS", TimeUnit.MILLISECONDS.toNanos(100));
        metrics.record("model", "SUCCESS", TimeUnit.MILLISECONDS.toNanos(200));
        metrics.record("model", "SUCCESS", TimeUnit.MILLISECONDS.toNanos(300));

        Timer timer = registry.get(AgentOperationMetrics.METRIC_NAME)
                .tag("stage", "model")
                .tag("resultCode", "SUCCESS")
                .timer();

        assertEquals(3L, timer.count());
        assertEquals(600.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
        assertEquals(200.0, timer.mean(TimeUnit.MILLISECONDS), 0.001);
        assertEquals(300.0, timer.max(TimeUnit.MILLISECONDS), 0.001);
    }

    @Test
    void unknownResultCodeIsCollapsedToBoundedTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOperationMetrics metrics = new AgentOperationMetrics(registry);

        metrics.record("tool", "ERROR_USER_1001", 1L);
        metrics.record("tool", "ERROR_USER_1002", 2L);

        Timer timer = registry.get(AgentOperationMetrics.METRIC_NAME)
                .tag("stage", "tool")
                .tag("resultCode", "UNKNOWN")
                .timer();

        assertEquals(2L, timer.count());
        assertEquals(1, registry.find(AgentOperationMetrics.METRIC_NAME)
                .timers().size());
    }

    @Test
    void invalidStageAndNegativeDurationAreRejected() {
        AgentOperationMetrics metrics =
                new AgentOperationMetrics(new SimpleMeterRegistry());

        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.record("database", "SUCCESS", 1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.record("model", "SUCCESS", -1L)
        );
    }

    @Test
    void metricTagsNeverContainPerRequestIdentity() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOperationMetrics metrics = new AgentOperationMetrics(registry);

        metrics.record("retrieval", "NO_RESULT", 1L);

        Set<String> tagKeys = registry.get(AgentOperationMetrics.METRIC_NAME)
                .timer()
                .getId()
                .getTags()
                .stream()
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());

        assertEquals(Set.of("stage", "resultCode"), tagKeys);
        assertFalse(tagKeys.contains("requestId"));
        assertFalse(tagKeys.contains("userId"));
        assertFalse(tagKeys.contains("sessionId"));
    }

    @Test
    void noopStillValidatesButDoesNotRequireARegistry() {
        AgentOperationMetrics metrics = AgentOperationMetrics.noop();

        metrics.record("model", "SUCCESS", 1L);
        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.record("unknown", "SUCCESS", 1L)
        );
    }
}
