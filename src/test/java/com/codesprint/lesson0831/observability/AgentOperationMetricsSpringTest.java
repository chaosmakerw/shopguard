package com.codesprint.lesson0831.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Spring 选择注入构造器，而不是 private noop 构造器。无 HTTP、数据库或外部模型。 */
@SpringBootTest
@ActiveProfiles("test")
class AgentOperationMetricsSpringTest {
    @Autowired AgentOperationMetrics metrics;
    @Autowired MeterRegistry registry;

    @Test
    void springBeanActuallyRecordsIntoInjectedRegistry() {
        var before = registry.find(AgentOperationMetrics.METRIC_NAME)
                .tags("stage", "model", "resultCode", "UNKNOWN").timer();
        long countBefore = before == null ? 0 : before.count();
        metrics.record("model", "UNRECOGNIZED_CODE", 1_000_000);
        var after = registry.find(AgentOperationMetrics.METRIC_NAME)
                .tags("stage", "model", "resultCode", "UNKNOWN").timer();
        assertThat(after).isNotNull();
        assertThat(after.count()).isEqualTo(countBefore + 1);
    }
}
