package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalResultTest {

    @Test
    void shouldCreateStableFactoryResults() {
        RetrievedChunk chunk = retrievedChunk("chunk-1", 0.9);

        RetrievalResult found = RetrievalResult.found(List.of(chunk));
        RetrievalResult noResult = RetrievalResult.noResult();
        RetrievalResult lowRelevance = RetrievalResult.lowRelevance();
        RetrievalResult unavailable = RetrievalResult.storeUnavailable();

        assertTrue(found.success());
        assertEquals(RetrievalCode.SUCCESS, found.code());
        assertEquals(List.of(chunk), found.chunks());

        assertTrue(noResult.success());
        assertEquals(RetrievalCode.NO_RESULT, noResult.code());
        assertTrue(noResult.chunks().isEmpty());

        assertTrue(lowRelevance.success());
        assertEquals(RetrievalCode.LOW_RELEVANCE, lowRelevance.code());
        assertTrue(lowRelevance.chunks().isEmpty());

        assertFalse(unavailable.success());
        assertEquals(RetrievalCode.VECTOR_STORE_UNAVAILABLE, unavailable.code());
        assertEquals("检索服务暂时不可用", unavailable.message());
        assertTrue(unavailable.chunks().isEmpty());
    }

    @Test
    void shouldDefensivelyCopyFoundChunks() {
        List<RetrievedChunk> source = new ArrayList<>();
        source.add(retrievedChunk("chunk-1", 0.9));

        RetrievalResult result = RetrievalResult.found(source);
        source.clear();

        assertEquals(1, result.chunks().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.chunks().clear()
        );
    }

    @Test
    void shouldRejectInconsistentSuccessResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievalResult(
                        false,
                        RetrievalCode.SUCCESS,
                        "错误组合",
                        List.of(retrievedChunk("chunk-1", 0.9))
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievalResult(
                        true,
                        RetrievalCode.SUCCESS,
                        "错误组合",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectChunksInNonSuccessResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievalResult(
                        true,
                        RetrievalCode.LOW_RELEVANCE,
                        "错误组合",
                        List.of(retrievedChunk("chunk-1", 0.2))
                )
        );
    }

    @Test
    void shouldRejectInvalidRetrievedChunk() {
        assertThrows(
                NullPointerException.class,
                () -> new RetrievedChunk(null, 0.5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievedChunk(
                        knowledgeChunk("chunk-1"),
                        Double.NaN
                )
        );
    }

    private RetrievedChunk retrievedChunk(String id, double score) {
        return new RetrievedChunk(knowledgeChunk(id), score);
    }

    private KnowledgeChunk knowledgeChunk(String id) {
        return new KnowledgeChunk(
                id,
                0,
                "退款规则",
                new KnowledgeDocumentMetadata(
                        "knowledge/refund.md",
                        "refund",
                        "v1",
                        DocumentVisibility.PUBLIC
                )
        );
    }
}
