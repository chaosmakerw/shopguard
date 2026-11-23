package com.codesprint.lesson0831.rag.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KnowledgeChunkEmbedder {

    private final TextEmbeddingModel model;

    public KnowledgeChunkEmbedder(
            TextEmbeddingModel model
    ) {
        this.model = Objects.requireNonNull(
                model,
                "Embedding模型不能为空"
        );
    }

    public EmbeddedChunk embed(
            KnowledgeChunk chunk
    ) {
        Objects.requireNonNull(
                chunk,
                "待向量化Chunk不能为空"
        );

        List<Double> vector =
                model.embed(chunk.text());

        return new EmbeddedChunk(
                chunk,
                vector
        );
    }

    public List<EmbeddedChunk> embedAll(
            List<KnowledgeChunk> chunks
    ) {
        Objects.requireNonNull(
                chunks,
                "待向量化Chunk列表不能为空"
        );

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "待向量化Chunk列表不能为空"
            );
        }

        List<KnowledgeChunk> sourceChunks =
                List.copyOf(chunks);

        List<EmbeddedChunk> results =
                new ArrayList<>(
                        sourceChunks.size()
                );

        for (KnowledgeChunk chunk
                : sourceChunks) {
            results.add(
                    embed(chunk)
            );
        }

        return List.copyOf(results);
    }
}