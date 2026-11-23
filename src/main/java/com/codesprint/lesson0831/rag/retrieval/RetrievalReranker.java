package com.codesprint.lesson0831.rag.retrieval;

import java.util.List;

public interface RetrievalReranker {

    List<RetrievedChunk> rerank(
            String query,
            List<RetrievedChunk> candidates
    );
}
