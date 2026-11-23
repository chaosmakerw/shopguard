package com.codesprint.lesson0831.rag.ingestion;

import java.util.Objects;

public record KnowledgeDocument(
        KnowledgeDocumentMetadata metadata,
        String content
) {
    public KnowledgeDocument {
        Objects.requireNonNull(
                metadata,
                "文档元数据不能为空"
        );

        Objects.requireNonNull(
                content,
                "文档正文不能为空"
        );

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "文档正文不能为空"
            );
        }
    }
}