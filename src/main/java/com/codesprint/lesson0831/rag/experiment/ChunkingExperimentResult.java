package com.codesprint.lesson0831.rag.experiment;

import java.util.List;
import java.util.Objects;

public record ChunkingExperimentResult(
        ChunkingExperimentConfiguration configuration,
        String query,
        int generatedChunkCount,
        List<ScoredChunk> topChunks
) {
    public ChunkingExperimentResult {
        Objects.requireNonNull(
                configuration,
                "实验配置不能为空"
        );
        Objects.requireNonNull(
                query,
                "实验问题不能为空"
        );
        Objects.requireNonNull(
                topChunks,
                "Top-K结果不能为空"
        );

        if (query.isBlank()) {
            throw new IllegalArgumentException(
                    "实验问题不能为空"
            );
        }

        if (generatedChunkCount <= 0) {
            throw new IllegalArgumentException(
                    "生成Chunk数量必须大于0"
            );
        }

        topChunks = List.copyOf(topChunks);
    }
}
