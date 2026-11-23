package com.codesprint.lesson0831.rag.ingestion;

import java.util.List;
import java.util.Objects;

public record EmbeddedChunk(
        KnowledgeChunk chunk,
        List<Double> vector
) {
    public EmbeddedChunk {
        Objects.requireNonNull(
                chunk,
                "Chunk不能为空"
        );

        Objects.requireNonNull(
                vector,
                "向量不能为空"
        );

        if (vector.isEmpty()) {
            throw new IllegalArgumentException(
                    "向量不能为空"
            );
        }

        List<Double> vectorCopy =
                List.copyOf(vector);

        for (Double value : vectorCopy) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "向量不能包含非有限数字"
                );
            }
        }

        vector = vectorCopy;
    }
}