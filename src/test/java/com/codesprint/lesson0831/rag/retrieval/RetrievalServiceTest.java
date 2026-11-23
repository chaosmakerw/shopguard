package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    @Test
    void shouldEmbedSearchFilterThresholdAndReturnSuccess() {
        TextEmbeddingModel embeddingModel =
                mock(TextEmbeddingModel.class);
        VectorSearchPort searchPort =
                mock(VectorSearchPort.class);
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);
        RetrievalRequest request = request(0.7);
        List<Double> queryVector = List.of(1.0, 0.0);
        RetrievedChunk high = retrievedChunk("high", 0.9);
        RetrievedChunk boundary = retrievedChunk("boundary", 0.7);
        RetrievedChunk low = retrievedChunk("low", 0.69);

        when(embeddingModel.embed(request.query()))
                .thenReturn(queryVector);
        when(searchPort.search(
                queryVector,
                request.version(),
                request.allowedVisibilities(),
                request.topK()
        )).thenReturn(List.of(high, boundary, low));

        RetrievalResult result = service.retrieve(request);

        assertTrue(result.success());
        assertEquals(RetrievalCode.SUCCESS, result.code());
        assertEquals(List.of(high, boundary), result.chunks());
        verify(embeddingModel).embed(request.query());
        verify(searchPort).search(
                queryVector,
                request.version(),
                request.allowedVisibilities(),
                request.topK()
        );
        verifyNoMoreInteractions(embeddingModel, searchPort);
    }

    @Test
    void shouldReturnNoResultWhenPortHasNoMetadataMatch() {
        TextEmbeddingModel embeddingModel =
                text -> List.of(1.0, 0.0);
        VectorSearchPort searchPort =
                (vector, version, visibilities, topK) -> List.of();
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);

        RetrievalResult result = service.retrieve(request(0.7));

        assertTrue(result.success());
        assertEquals(RetrievalCode.NO_RESULT, result.code());
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void shouldReturnLowRelevanceWhenAllCandidatesAreBelowThreshold() {
        TextEmbeddingModel embeddingModel =
                text -> List.of(1.0, 0.0);
        VectorSearchPort searchPort =
                (vector, version, visibilities, topK) ->
                        List.of(retrievedChunk("low", 0.69));
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);

        RetrievalResult result = service.retrieve(request(0.7));

        assertTrue(result.success());
        assertEquals(RetrievalCode.LOW_RELEVANCE, result.code());
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void shouldMapVectorStoreExceptionToStableUnavailableResult() {
        TextEmbeddingModel embeddingModel =
                text -> List.of(1.0, 0.0);
        VectorSearchPort searchPort =
                (vector, version, visibilities, topK) -> {
                    throw new IllegalStateException(
                            "qdrant://secret-host:6333 refused"
                    );
                };
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);

        RetrievalResult result = service.retrieve(request(0.7));

        assertFalse(result.success());
        assertEquals(
                RetrievalCode.VECTOR_STORE_UNAVAILABLE,
                result.code()
        );
        assertEquals("检索服务暂时不可用", result.message());
        assertFalse(result.message().contains("secret-host"));
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void shouldTreatNullPortResponseAsUnavailable() {
        TextEmbeddingModel embeddingModel =
                text -> List.of(1.0, 0.0);
        VectorSearchPort searchPort =
                (vector, version, visibilities, topK) -> null;
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);

        RetrievalResult result = service.retrieve(request(0.7));

        assertFalse(result.success());
        assertEquals(
                RetrievalCode.VECTOR_STORE_UNAVAILABLE,
                result.code()
        );
    }

    @Test
    void shouldFailBeforeDependenciesWhenRequestIsNull() {
        TextEmbeddingModel embeddingModel =
                mock(TextEmbeddingModel.class);
        VectorSearchPort searchPort =
                mock(VectorSearchPort.class);
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);

        assertThrows(
                NullPointerException.class,
                () -> service.retrieve(null)
        );

        verifyNoInteractions(embeddingModel, searchPort);
    }

    @Test
    void shouldNotMislabelEmbeddingFailureAsVectorStoreFailure() {
        TextEmbeddingModel embeddingModel =
                mock(TextEmbeddingModel.class);
        VectorSearchPort searchPort =
                mock(VectorSearchPort.class);
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);
        RetrievalRequest request = request(0.7);
        IllegalStateException expected =
                new IllegalStateException("embedding unavailable");

        when(embeddingModel.embed(request.query()))
                .thenThrow(expected);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> service.retrieve(request)
        );

        assertEquals(expected, actual);
        verify(embeddingModel).embed(request.query());
        verifyNoInteractions(searchPort);
        verifyNoMoreInteractions(embeddingModel);
    }

    private RetrievalRequest request(double minScore) {
        return new RetrievalRequest(
                "退款规则",
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                3,
                minScore
        );
    }

    private RetrievedChunk retrievedChunk(String id, double score) {
        return new RetrievedChunk(
                new KnowledgeChunk(
                        id,
                        0,
                        "退款规则-" + id,
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
}
