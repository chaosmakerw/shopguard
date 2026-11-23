package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ModelCallException;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public class TimeBudget {
    private final LongSupplier nanoTime;
    private final long startedAt;
    private final long budgetNanos;

    public TimeBudget(Duration timeout, LongSupplier nanoTime) {
        Objects.requireNonNull(timeout);

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("超时时间必须大于零");
        }

        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.budgetNanos = timeout.toNanos();
        this.startedAt = nanoTime.getAsLong();
    }

    public void checkNotExpired() {
        long elapsed = nanoTime.getAsLong() - startedAt;

        if (elapsed >= budgetNanos) {
            throw new ModelCallException("工具调用流程已超时");
        }
    }
    public long remainingMillis() {
        long elapsed = nanoTime.getAsLong() - startedAt;
        long lastTime = budgetNanos - elapsed;
        long remainingMillis = lastTime / 1_000_000L;
        if(remainingMillis <= 0){
            throw new ModelCallException("工具调用流程已超时");
        }
        return remainingMillis;
    }
}