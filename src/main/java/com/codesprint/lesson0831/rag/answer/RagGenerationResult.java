package com.codesprint.lesson0831.rag.answer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RagGenerationResult(
        boolean answered,
        String answer,
        List<String> citedChunkIds
) {

    public RagGenerationResult {
        Objects.requireNonNull(answer, "生成结果文本不能为空");
        Objects.requireNonNull(
                citedChunkIds,
                "引用Chunk ID列表不能为空"
        );

        citedChunkIds = List.copyOf(citedChunkIds);

        Set<String> uniqueIds = new HashSet<>();
        for (String chunkId : citedChunkIds) {
            if (chunkId == null || chunkId.isBlank()) {
                throw new IllegalArgumentException(
                        "引用Chunk ID不能为空"
                );
            }
            if (!uniqueIds.add(chunkId)) {
                throw new IllegalArgumentException(
                        "引用Chunk ID不能重复"
                );
            }
        }

        if (answered && answer.isBlank()) {
            throw new IllegalArgumentException(
                    "已回答时文本不能为空"
            );
        }

        if (answered && citedChunkIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "已回答时必须声明引用Chunk ID"
            );
        }

        if (!answered && !answer.isEmpty()) {
            throw new IllegalArgumentException(
                    "未回答时不能携带答案文本"
            );
        }

        if (!answered && !citedChunkIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "未回答时不能携带引用Chunk ID"
            );
        }
    }

    public static RagGenerationResult answered(
            String answer,
            List<String> citedChunkIds
    ) {
        return new RagGenerationResult(
                true,
                answer,
                citedChunkIds
        );
    }

    public static RagGenerationResult noAnswer() {
        return new RagGenerationResult(
                false,
                "",
                List.of()
        );
    }
}
