package com.codesprint.lesson0831.rag.answer;

import com.codesprint.lesson0831.rag.ingestion.ClasspathKnowledgeDocumentLoader;
import com.codesprint.lesson0831.rag.ingestion.DeterministicFakeEmbeddingModel;
import com.codesprint.lesson0831.rag.ingestion.InMemoryVectorStore;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunkEmbedder;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentCatalog;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentChunker;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentPreprocessor;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeIngestionService;
import com.codesprint.lesson0831.rag.retrieval.CharacterBigramHybridReranker;
import com.codesprint.lesson0831.rag.retrieval.InMemoryVectorSearchAdapter;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import com.codesprint.lesson0831.rag.retrieval.TrustedRetrievalRequestFactory;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HybridRagAnswerPipelineIntegrationTest {

    @Test
    void noAnswerQueryStopsBeforeGenerator() {
        RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
        RagAnswerService service = new RagAnswerService(
                retrieval(), new RagPromptBuilder(), generator);
        RetrievalRequest request = new TrustedRetrievalRequestFactory(
                new FakeCurrentUserContext(null, false))
                .create("周末人工客服的上班时间是几点？",
                        "2026-09-14", 3, 0.40);

        RagAnswerResult result = service.answer(request);

        assertEquals(RagAnswerCode.INSUFFICIENT_EVIDENCE, result.code());
        verifyNoInteractions(generator);
    }

    @Test
    void refundEvidenceReachesPromptAndCitationUsesRealMetadata() {
        RagPrompt[] seenPrompt = new RagPrompt[1];
        RagAnswerService service = new RagAnswerService(
                retrieval(), new RagPromptBuilder(), prompt -> {
                    seenPrompt[0] = prompt;
                    return RagGenerationResult.answered(
                            "已发货订单不能直接取消，先申请退货。",
                            java.util.List.of(
                                    "knowledge/refund-policy.md::2026-09-14::0"
                            )
                    );
                });
        RetrievalRequest request = new TrustedRetrievalRequestFactory(
                new FakeCurrentUserContext(1001L, false))
                .create("已经发货的订单能直接取消吗？应该先做什么？",
                        "2026-09-14", 3, 0.40);

        RagAnswerResult result = service.answer(request);

        assertEquals(RagAnswerCode.ANSWERED, result.code());
        assertNotNull(seenPrompt[0]);
        assertTrue(seenPrompt[0].context().contains("已发货订单不能直接取消"));
        assertTrue(result.citations().stream().anyMatch(citation ->
                "knowledge/refund-policy.md".equals(citation.source())));
    }

    private RetrievalService retrieval() {
        DeterministicFakeEmbeddingModel embedding = new DeterministicFakeEmbeddingModel(64);
        InMemoryVectorStore store = new InMemoryVectorStore(64);
        KnowledgeIngestionService ingestion = new KnowledgeIngestionService(
                new ClasspathKnowledgeDocumentLoader(),
                new KnowledgeDocumentPreprocessor(),
                new KnowledgeDocumentChunker(256, 32),
                new KnowledgeChunkEmbedder(embedding), store);
        ingestion.ingest(KnowledgeDocumentCatalog.standardMetadata());
        return new RetrievalService(embedding,
                new InMemoryVectorSearchAdapter(store),
                new CharacterBigramHybridReranker());
    }
}
