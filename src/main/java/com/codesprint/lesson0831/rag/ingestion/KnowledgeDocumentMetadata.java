package com.codesprint.lesson0831.rag.ingestion;

import java.util.Objects;

public record KnowledgeDocumentMetadata(
        String source,
        String section,
        String version,
        DocumentVisibility visibility
) {
    public KnowledgeDocumentMetadata {
        source = requireText(
                source,
                "文档来源不能为空"
        );

        section = requireText(
                section,
                "文档章节不能为空"
        );

        version = requireText(
                version,
                "文档版本不能为空"
        );

        Objects.requireNonNull(
                visibility,
                "文档可见范围不能为空"
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