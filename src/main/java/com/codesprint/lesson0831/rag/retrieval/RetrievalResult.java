package com.codesprint.lesson0831.rag.retrieval;

import java.util.List;
import java.util.Objects;

public record RetrievalResult(
        boolean success,
        RetrievalCode code,
        String message,
        List<RetrievedChunk> chunks
) {

    public RetrievalResult {
        Objects.requireNonNull(
                code,
                "检索结果码不能为空"
        );

        message = requireText(
                message,
                "检索结果消息不能为空"
        );

        Objects.requireNonNull(
                chunks,
                "检索结果列表不能为空"
        );

        chunks = List.copyOf(chunks);

        if (code == RetrievalCode.SUCCESS) {
            if (!success) {
                throw new IllegalArgumentException(
                        "SUCCESS结果的success必须为true"
                );
            }

            if (chunks.isEmpty()) {
                throw new IllegalArgumentException(
                        "SUCCESS结果必须包含召回Chunk"
                );
            }
        } else if (!chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "非SUCCESS结果不能包含召回Chunk"
            );
        }

        if (code == RetrievalCode.VECTOR_STORE_UNAVAILABLE
                && success) {
            throw new IllegalArgumentException(
                    "向量库故障结果的success必须为false"
            );
        }

        if ((code == RetrievalCode.NO_RESULT
                || code == RetrievalCode.LOW_RELEVANCE)
                && !success) {
            throw new IllegalArgumentException(
                    "正常完成的空结果success必须为true"
            );
        }
    }

    public static RetrievalResult found(
            List<RetrievedChunk> chunks
    ) {
        return new RetrievalResult(
                true,
                RetrievalCode.SUCCESS,
                "检索成功",
                chunks
        );
    }

    public static RetrievalResult noResult() {
        return new RetrievalResult(
                true,
                RetrievalCode.NO_RESULT,
                "未找到符合版本和访问范围的知识",
                List.of()
        );
    }

    public static RetrievalResult lowRelevance() {
        return new RetrievalResult(
                true,
                RetrievalCode.LOW_RELEVANCE,
                "未找到相关度足够的知识",
                List.of()
        );
    }

    public static RetrievalResult storeUnavailable() {
        return new RetrievalResult(
                false,
                RetrievalCode.VECTOR_STORE_UNAVAILABLE,
                "检索服务暂时不可用",
                List.of()
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