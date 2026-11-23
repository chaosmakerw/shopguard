package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;

import java.util.List;
import java.util.Set;

@FunctionalInterface
public interface VectorSearchPort {

    List<RetrievedChunk> search(
            List<Double> queryVector,
            String version,
            Set<DocumentVisibility> allowedVisibilities,
            int topK
    );
}