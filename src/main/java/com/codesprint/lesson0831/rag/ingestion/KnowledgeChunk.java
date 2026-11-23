package com.codesprint.lesson0831.rag.ingestion;

import java.util.Objects;

public record KnowledgeChunk(
        String id,
        int index,
        String text,
        KnowledgeDocumentMetadata metadata
) {
    public KnowledgeChunk {
        id = requireText(
                id,
                "Chunk ID不能为空"
        );

        if (index < 0) {
            throw new IllegalArgumentException(
                    "Chunk索引不能小于0"
            );
        }

        text = requireText(
                text,
                "Chunk文本不能为空"
        );

        Objects.requireNonNull(
                metadata,
                "Chunk元数据不能为空"
        );
    }

    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(
                value,
                message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }
}