package com.codesprint.lesson0831.rag.answer;

import com.codesprint.lesson0831.rag.ingestion.DeterministicFakeEmbeddingModel;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.EmbeddedChunk;
import com.codesprint.lesson0831.rag.ingestion.InMemoryVectorStore;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import com.codesprint.lesson0831.rag.ingestion.VectorStore;
import com.codesprint.lesson0831.rag.retrieval.InMemoryVectorSearchAdapter;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAnswerPipelineIntegrationTest {

    @Test
    void shouldAnswerUsingRealRetrievalComponentsAndFakeGenerator() {
        TextEmbeddingModel embedding = new DeterministicFakeEmbeddingModel(16);
        VectorStore store = new InMemoryVectorStore(16);
        String text = "已发货订单先申请退货。";
        store.upsert(new EmbeddedChunk(
                new KnowledgeChunk("refund-v1", 0, text,
                        new KnowledgeDocumentMetadata("knowledge/refund.md",
                                "refund", "v1", DocumentVisibility.PUBLIC)),
                embedding.embed(text)
        ));
        store.upsert(new EmbeddedChunk(
                new KnowledgeChunk("admin-v1", 0, text,
                        new KnowledgeDocumentMetadata("knowledge/admin.md",
                                "admin", "v1", DocumentVisibility.ADMIN_ONLY)),
                embedding.embed(text)
        ));
        RetrievalService retrieval = new RetrievalService(
                embedding, new InMemoryVectorSearchAdapter(store));
        RagAnswerService service = new RagAnswerService(
                retrieval,
                new RagPromptBuilder(),
                prompt -> {
                    assertTrue(prompt.context().contains("chunkId: refund-v1"));
                    assertTrue(!prompt.context().contains("chunkId: admin-v1"));
                    return RagGenerationResult.answered(
                            "先申请退货。",
                            java.util.List.of("refund-v1")
                    );
                }
        );

        RagAnswerResult result = service.answer(new RetrievalRequest(
                text,
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                3,
                0.99
        ));

        assertEquals(RagAnswerCode.ANSWERED, result.code());
        assertEquals(1, result.citations().size());
        assertEquals("refund-v1", result.citations().get(0).chunkId());
        assertEquals("knowledge/refund.md", result.citations().get(0).source());
    }
}
