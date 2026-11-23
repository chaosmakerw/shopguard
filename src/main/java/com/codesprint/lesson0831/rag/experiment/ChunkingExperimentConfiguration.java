package com.codesprint.lesson0831.rag.experiment;

import java.util.Objects;

public record ChunkingExperimentConfiguration(
        String name,
        int chunkSize,
        int overlap
) {
    public ChunkingExperimentConfiguration {
        Objects.requireNonNull(
                name,
                "实验配置名称不能为空"
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "实验配置名称不能为空"
            );
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize必须大于0"
            );
        }

        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "overlap必须大于等于0且小于chunkSize"
            );
        }
    }
}
