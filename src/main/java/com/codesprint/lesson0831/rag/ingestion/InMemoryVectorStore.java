package com.codesprint.lesson0831.rag.ingestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class InMemoryVectorStore
        implements VectorStore {

    private final int dimensions;

    private final Map<String, EmbeddedChunk> entries =
            new LinkedHashMap<>();

    public InMemoryVectorStore(
            int dimensions
    ) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException(
                    "向量维度必须大于0"
            );
        }

        this.dimensions = dimensions;
    }

    @Override
    public VectorWriteStatus upsert(
            EmbeddedChunk chunk
    ) {
        Objects.requireNonNull(
                chunk,
                "待写入Chunk不能为空"
        );

        validateDimensions(chunk);

        String id = chunk.chunk().id();

        EmbeddedChunk existing =
                entries.get(id);

        if (existing == null) {
            entries.put(id, chunk);
            return VectorWriteStatus.CREATED;
        }

        if (existing.equals(chunk)) {
            return VectorWriteStatus.UNCHANGED;
        }

        entries.put(id, chunk);
        return VectorWriteStatus.UPDATED;
    }

    @Override
    public VectorWriteSummary upsertAll(
            List<EmbeddedChunk> chunks
    ) {
        Objects.requireNonNull(
                chunks,
                "待写入Chunk列表不能为空"
        );

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "待写入Chunk列表不能为空"
            );
        }

        List<EmbeddedChunk> sourceChunks =
                List.copyOf(chunks);

        for (EmbeddedChunk chunk : sourceChunks) {
            validateDimensions(chunk);
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (EmbeddedChunk chunk : sourceChunks) {
            VectorWriteStatus status =
                    upsert(chunk);

            switch (status) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }

        return new VectorWriteSummary(
                created,
                updated,
                unchanged
        );
    }

    @Override
    public Optional<EmbeddedChunk> findById(
            String id
    ) {
        requireText(
                id,
                "Chunk ID不能为空"
        );

        return Optional.ofNullable(
                entries.get(id)
        );
    }

    @Override
    public List<EmbeddedChunk> findAll() {
        return List.copyOf(
                entries.values()
        );
    }

    @Override
    public int size() {
        return entries.size();
    }

    private void validateDimensions(
            EmbeddedChunk chunk
    ) {
        Objects.requireNonNull(
                chunk,
                "待写入Chunk不能为空"
        );

        if (chunk.vector().size()
                != dimensions) {
            throw new IllegalArgumentException(
                    "向量维度不匹配，期望"
                            + dimensions
                            + "，实际"
                            + chunk.vector().size()
            );
        }
    }

    private String requireText(
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