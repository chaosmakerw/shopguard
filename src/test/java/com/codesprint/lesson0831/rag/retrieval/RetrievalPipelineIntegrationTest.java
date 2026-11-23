package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DeterministicFakeEmbeddingModel;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.EmbeddedChunk;
import com.codesprint.lesson0831.rag.ingestion.InMemoryVectorStore;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import com.codesprint.lesson0831.rag.ingestion.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalPipelineIntegrationTest {

    @Test
    void shouldRetrieveThroughRealFakeEmbeddingStoreAdapterAndService() {
        TextEmbeddingModel embeddingModel =
                new DeterministicFakeEmbeddingModel(16);
        VectorStore store = new InMemoryVectorStore(16);
        store.upsert(embeddedChunk(
                embeddingModel,
                "public-v1",
                "退款规则",
                "v1",
                DocumentVisibility.PUBLIC
        ));
        store.upsert(embeddedChunk(
                embeddingModel,
                "admin-v1",
                "退款规则",
                "v1",
                DocumentVisibility.ADMIN_ONLY
        ));
        store.upsert(embeddedChunk(
                embeddingModel,
                "public-v2",
                "退款规则",
                "v2",
                DocumentVisibility.PUBLIC
        ));
        VectorSearchPort searchPort =
                new InMemoryVectorSearchAdapter(store);
        RetrievalService service =
                new RetrievalService(embeddingModel, searchPort);

        RetrievalResult result = service.retrieve(
                new RetrievalRequest(
                        "退款规则",
                        "v1",
                        Set.of(DocumentVisibility.PUBLIC),
                        3,
                        0.99
                )
        );

        assertTrue(result.success());
        assertEquals(RetrievalCode.SUCCESS, result.code());
        assertEquals(1, result.chunks().size());
        assertEquals("public-v1", result.chunks().get(0).chunk().id());
        assertEquals(1.0, result.chunks().get(0).score(), 0.000001);
    }

    @Test
    void shouldReturnNoResultThroughRealComponentsForUnknownVersion() {
        TextEmbeddingModel embeddingModel =
                new DeterministicFakeEmbeddingModel(16);
        VectorStore store = new InMemoryVectorStore(16);
        store.upsert(embeddedChunk(
                embeddingModel,
                "public-v1",
                "退款规则",
                "v1",
                DocumentVisibility.PUBLIC
        ));
        RetrievalService service = new RetrievalService(
                embeddingModel,
                new InMemoryVectorSearchAdapter(store)
        );

        RetrievalResult result = service.retrieve(
                new RetrievalRequest(
                        "退款规则",
                        "v2",
                        Set.of(DocumentVisibility.PUBLIC),
                        3,
                        0.5
                )
        );

        assertTrue(result.success());
        assertEquals(RetrievalCode.NO_RESULT, result.code());
        assertTrue(result.chunks().isEmpty());
    }

    private EmbeddedChunk embeddedChunk(
            TextEmbeddingModel embeddingModel,
            String id,
            String text,
            String version,
            DocumentVisibility visibility
    ) {
        return new EmbeddedChunk(
                new KnowledgeChunk(
                        id,
                        0,
                        text,
                        new KnowledgeDocumentMetadata(
                                "knowledge/" + id + ".md",
                                "refund",
                                version,
                                visibility
                        )
                ),
                embeddingModel.embed(text)
        );
    }
}
