package com.codesprint.lesson0831.rag.experiment;

import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;

import java.util.Objects;

public record ScoredChunk(
        KnowledgeChunk chunk,
        double score
) {
    public ScoredChunk {
        Objects.requireNonNull(
                chunk,
                "召回Chunk不能为空"
        );

        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException(
                    "相似度分数必须是有限数字"
            );
        }
    }
}
