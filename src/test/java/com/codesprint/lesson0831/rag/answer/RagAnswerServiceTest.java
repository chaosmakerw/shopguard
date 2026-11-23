package com.codesprint.lesson0831.rag.answer;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import com.codesprint.lesson0831.rag.retrieval.RetrievedChunk;
import com.codesprint.lesson0831.rag.retrieval.VectorSearchPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagAnswerServiceTest {

    @Test
    void shouldGenerateAnswerAndBindCitationsToRetrievedChunks() {
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        RetrievedChunk evidence = chunk("refund-1", 0.9);
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> List.of(evidence),
                generator
        );
        when(generator.generate(any(RagPrompt.class)))
                .thenReturn(RagGenerationResult.answered(
                        "先申请退货。",
                        List.of("refund-1")
                ));

        RagAnswerResult result = service.answer(request(0.7));

        assertEquals(RagAnswerCode.ANSWERED, result.code());
        assertEquals("先申请退货。", result.answer());
        assertEquals(List.of(RagCitation.from(evidence)), result.citations());
        ArgumentCaptor<RagPrompt> prompt = ArgumentCaptor.forClass(RagPrompt.class);
        verify(generator).generate(prompt.capture());
        assertTrue(prompt.getValue().context().contains("chunkId: refund-1"));
        assertEquals("已发货订单如何处理？", prompt.getValue().question());
    }

    @Test
    void shouldNotCallGeneratorWhenNoMatchingEvidenceExists() {
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> List.of(), generator);

        RagAnswerResult result = service.answer(request(0.7));

        assertEquals(RagAnswerCode.INSUFFICIENT_EVIDENCE, result.code());
        assertTrue(result.citations().isEmpty());
        verifyNoInteractions(generator);
    }

    @Test
    void shouldNotCallGeneratorWhenSimilarityIsTooLow() {
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> List.of(chunk("low", 0.2)),
                generator
        );

        RagAnswerResult result = service.answer(request(0.7));

        assertEquals(RagAnswerCode.INSUFFICIENT_EVIDENCE, result.code());
        verifyNoInteractions(generator);
    }

    @Test
    void shouldMapVectorStoreFaultWithoutCallingGenerator() {
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> {
                    throw new IllegalStateException("internal-vector-address");
                },
                generator
        );

        RagAnswerResult result = service.answer(request(0.7));

        assertFalse(result.success());
        assertEquals(RagAnswerCode.RETRIEVAL_UNAVAILABLE, result.code());
        assertFalse(result.answer().contains("internal-vector-address"));
        verifyNoInteractions(generator);
    }

    @Test
    void shouldMapEmbeddingFailureWithoutCallingSearchOrGenerator() {
        VectorSearchPort searchPort = mock(VectorSearchPort.class);
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        TextEmbeddingModel failedEmbedding = text -> {
            throw new IllegalStateException("internal-embedding-address");
        };
        RagAnswerService service = new RagAnswerService(
                new RetrievalService(failedEmbedding, searchPort),
                new RagPromptBuilder(),
                generator
        );

        RagAnswerResult result = service.answer(request(0.7));

        assertEquals(RagAnswerCode.RETRIEVAL_UNAVAILABLE, result.code());
        assertFalse(result.answer().contains("internal-embedding-address"));
        verifyNoInteractions(searchPort, generator);
    }

    @Test
    void generatorRefusalShouldNotCreateMisleadingCitations() {
        RagAnswerGenerator generator = prompt -> RagGenerationResult.noAnswer();
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> List.of(chunk("id", 0.9)),
                generator
        );

        RagAnswerResult result = service.answer(request(0.7));

        assertEquals(RagAnswerCode.INSUFFICIENT_EVIDENCE, result.code());
        assertTrue(result.citations().isEmpty());
    }

    @Test
    void generatorFaultShouldReturnStableFailureWithoutCitations() {
        RagAnswerGenerator generator = prompt -> {
            throw new IllegalStateException("internal-model-key");
        };
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> List.of(chunk("id", 0.9)),
                generator
        );

        RagAnswerResult result = service.answer(request(0.7));

        assertFalse(result.success());
        assertEquals(RagAnswerCode.GENERATION_UNAVAILABLE, result.code());
        assertFalse(result.answer().contains("internal-model-key"));
        assertTrue(result.citations().isEmpty());
    }

    @Test
    void nullGeneratorResponseShouldReturnStableFailure() {
        RagAnswerService service = service(
                (vector, version, visibility, topK) -> List.of(chunk("id", 0.9)),
                prompt -> null
        );

        RagAnswerResult result = service.answer(request(0.7));

        assertEquals(RagAnswerCode.GENERATION_UNAVAILABLE, result.code());
    }

    @Test
    void nullRequestShouldFailBeforeCallingDependencies() {
        VectorSearchPort searchPort = mock(VectorSearchPort.class);
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        RagAnswerService service = service(searchPort, generator);

        assertThrows(NullPointerException.class, () -> service.answer(null));

        verifyNoInteractions(searchPort, generator);
    }
    @Test
    void shouldReturnOnlyCitationsDeclaredByGenerator() {
        RagAnswerGenerator generator =
                mock(RagAnswerGenerator.class);

        RetrievedChunk usedEvidence =
                chunk("refund-1", 0.9);

        RetrievedChunk unusedEvidence =
                chunk("trading-1", 0.8);

        RagAnswerService service = service(
                (vector, version, visibility, topK) ->
                        List.of(
                                usedEvidence,
                                unusedEvidence
                        ),
                generator
        );

        when(generator.generate(any(RagPrompt.class)))
                .thenReturn(
                        RagGenerationResult.answered(
                                "已发货订单需要先申请退货。",
                                List.of("refund-1")
                        )
                );

        RagAnswerResult result =
                service.answer(request(0.7));

        assertTrue(result.success());
        assertEquals(
                RagAnswerCode.ANSWERED,
                result.code()
        );
        assertEquals(
                "已发货订单需要先申请退货。",
                result.answer()
        );
        assertEquals(
                List.of(
                        RagCitation.from(usedEvidence)
                ),
                result.citations()
        );

        verify(generator)
                .generate(any(RagPrompt.class));
    }
    @Test
    void shouldRejectCitationIdThatWasNotRetrieved() {
        RagAnswerGenerator generator =
                mock(RagAnswerGenerator.class);

        RetrievedChunk retrievedEvidence =
                chunk("refund-1", 0.9);

        RagAnswerService service = service(
                (vector, version, visibility, topK) ->
                        List.of(retrievedEvidence),
                generator
        );

        when(generator.generate(any(RagPrompt.class)))
                .thenReturn(
                        RagGenerationResult.answered(
                                "请查看管理员内部规则。",
                                List.of("forged-admin-chunk")
                        )
                );

        RagAnswerResult result =
                service.answer(request(0.7));

        assertFalse(result.success());
        assertEquals(
                RagAnswerCode.GENERATION_UNAVAILABLE,
                result.code()
        );
        assertTrue(result.citations().isEmpty());

        verify(generator)
                .generate(any(RagPrompt.class));
    }
    private RagAnswerService service(
            VectorSearchPort searchPort,
            RagAnswerGenerator generator
    ) {
        TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);
        return new RagAnswerService(
                new RetrievalService(embedding, searchPort),
                new RagPromptBuilder(),
                generator
        );
    }

    private RetrievalRequest request(double minScore) {
        return new RetrievalRequest(
                "已发货订单如何处理？",
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                3,
                minScore
        );
    }

    private RetrievedChunk chunk(String id, double score) {
        return new RetrievedChunk(
                new KnowledgeChunk(id, 0, "已发货订单先申请退货。",
                        new KnowledgeDocumentMetadata("knowledge/refund.md",
                                "refund", "v1", DocumentVisibility.PUBLIC)),
                score
        );
    }
}
