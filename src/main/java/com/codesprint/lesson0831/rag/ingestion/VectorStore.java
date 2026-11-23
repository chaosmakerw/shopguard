package com.codesprint.lesson0831.rag.ingestion;

import java.util.List;
import java.util.Optional;

public interface VectorStore {

    VectorWriteStatus upsert(
            EmbeddedChunk chunk
    );

    VectorWriteSummary upsertAll(
            List<EmbeddedChunk> chunks
    );

    Optional<EmbeddedChunk> findById(
            String id
    );

    List<EmbeddedChunk> findAll();

    int size();
}
