package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ModelCallException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TimeBudgetTest {

    @Test
    void shouldAllowTimeBeforeDeadline() {
        AtomicLong now = new AtomicLong(100L);

        TimeBudget budget = new TimeBudget(
                Duration.ofNanos(10),
                () -> now.get()
        );

        now.set(109L);

        assertDoesNotThrow(() -> budget.checkNotExpired());
    }
    @Test
    void shouldExpireAtDeadlineWithoutResettingBudget() {
        AtomicLong now = new AtomicLong(100L);

        TimeBudget budget = new TimeBudget(
                Duration.ofNanos(10),
                () -> now.get()
        );

        now.set(106L);
        assertDoesNotThrow(() -> budget.checkNotExpired());

        now.set(110L);
        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> budget.checkNotExpired()
        );

        assertEquals("工具调用流程已超时", exception.getMessage());
    }
    @Test
    void shouldRejectNonPositiveTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeBudget(Duration.ZERO, () -> 100L)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeBudget(
                        Duration.ofSeconds(-1),
                        () -> 100L
                )
        );
    }
}