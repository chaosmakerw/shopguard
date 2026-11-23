package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;

import java.util.Objects;

public record RetrievedChunk(
        KnowledgeChunk chunk,
        double score
) {

    public RetrievedChunk {
        Objects.requireNonNull(
                chunk,
                "召回Chunk不能为空"
        );

        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException(
                    "相关度分数必须是有限数字"
            );
        }
    }
}
