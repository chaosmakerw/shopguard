package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;

import java.util.Objects;
import java.util.Set;

public record RetrievalRequest(
        String query,
        String version,
        Set<DocumentVisibility> allowedVisibilities,
        int topK,
        double minScore
) {

    public RetrievalRequest {
        query = requireText(
                query,
                "检索问题不能为空"
        );

        version = requireText(
                version,
                "检索版本不能为空"
        );

        Objects.requireNonNull(
                allowedVisibilities,
                "允许访问的文档范围不能为空"
        );

        if (allowedVisibilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "允许访问的文档范围不能为空"
            );
        }

        allowedVisibilities =
                Set.copyOf(allowedVisibilities);

        if (topK <= 0) {
            throw new IllegalArgumentException(
                    "topK必须大于0"
            );
        }

        if (!Double.isFinite(minScore)
                || minScore < -1.0
                || minScore > 1.0) {
            throw new IllegalArgumentException(
                    "最低相关度必须是-1到1之间的有限数字"
            );
        }
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
