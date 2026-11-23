package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetrievalMetricsTest {

    @Test
    void shouldRecordEveryNormalRetrievalOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOperationMetrics metrics = new AgentOperationMetrics(registry);
        TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);

        RetrievalResult noResult = service(
                embedding,
                (vector, version, visibilities, topK) -> List.of(),
                metrics
        ).retrieve(request());

        RetrievalResult lowRelevance = service(
                embedding,
                (vector, version, visibilities, topK) ->
                        List.of(chunk("low", 0.69)),
                metrics
        ).retrieve(request());

        RetrievalResult success = service(
                embedding,
                (vector, version, visibilities, topK) ->
                        List.of(chunk("high", 0.91)),
                metrics
        ).retrieve(request());

        assertEquals(RetrievalCode.NO_RESULT, noResult.code());
        assertEquals(RetrievalCode.LOW_RELEVANCE, lowRelevance.code());
        assertEquals(RetrievalCode.SUCCESS, success.code());
        assertTimerCount(registry, "NO_RESULT", 1L);
        assertTimerCount(registry, "LOW_RELEVANCE", 1L);
        assertTimerCount(registry, "SUCCESS", 1L);
    }

    @Test
    void shouldRecordVectorStoreFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOperationMetrics metrics = new AgentOperationMetrics(registry);
        TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);
        VectorSearchPort failingSearch =
                (vector, version, visibilities, topK) -> {
                    throw new IllegalStateException("secret vector address");
                };

        RetrievalResult result = service(
                embedding,
                failingSearch,
                metrics
        ).retrieve(request());

        assertEquals(
                RetrievalCode.VECTOR_STORE_UNAVAILABLE,
                result.code()
        );
        assertTimerCount(
                registry,
                "VECTOR_STORE_UNAVAILABLE",
                1L
        );
    }

    private RetrievalService service(
            TextEmbeddingModel embedding,
            VectorSearchPort search,
            AgentOperationMetrics metrics
    ) {
        return new RetrievalService(
                embedding,
                search,
                null,
                metrics
        );
    }

    private RetrievalRequest request() {
        return new RetrievalRequest(
                "退款规则",
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                3,
                0.7
        );
    }

    private RetrievedChunk chunk(String id, double score) {
        return new RetrievedChunk(
                new KnowledgeChunk(
                        id,
                        0,
                        "退款规则",
                        new KnowledgeDocumentMetadata(
                                "knowledge/refund.md",
                                "refund",
                                "v1",
                                DocumentVisibility.PUBLIC
                        )
                ),
                score
        );
    }

    private void assertTimerCount(
            SimpleMeterRegistry registry,
            String resultCode,
            long expectedCount
    ) {
        Timer timer = registry.find(
                        AgentOperationMetrics.METRIC_NAME
                )
                .tag("stage", "retrieval")
                .tag("resultCode", resultCode)
                .timer();

        assertNotNull(timer);
        assertEquals(expectedCount, timer.count());
    }
}
