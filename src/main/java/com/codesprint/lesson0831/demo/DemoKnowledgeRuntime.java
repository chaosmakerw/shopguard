package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import com.codesprint.lesson0831.rag.answer.*;
import com.codesprint.lesson0831.rag.ingestion.*;
import com.codesprint.lesson0831.rag.retrieval.*;
import java.util.List;
import java.util.Map;

/** 演示文档入库和查询共享同一真实流水线；串行锁仅适合本机作品展示。 */
public final class DemoKnowledgeRuntime {
    private final InMemoryVectorStore store = new InMemoryVectorStore(64);
    private final DeterministicFakeEmbeddingModel embedding = new DeterministicFakeEmbeddingModel(64);
    private final KnowledgeIngestionService ingestion;
    private final RetrievalService retrieval;
    private IngestionReport latest;
    public DemoKnowledgeRuntime(AgentOperationMetrics metrics) {
        ingestion = new KnowledgeIngestionService(new ClasspathKnowledgeDocumentLoader(),
                new KnowledgeDocumentPreprocessor(), new KnowledgeDocumentChunker(256, 32),
                new KnowledgeChunkEmbedder(embedding), store);
        retrieval = new RetrievalService(embedding, new InMemoryVectorSearchAdapter(store),
                new CharacterBigramHybridReranker(), metrics);
        latest = ingestion.ingest(KnowledgeDocumentCatalog.standardMetadata());
    }
    public synchronized IngestionReport reimport() {
        latest = ingestion.ingest(KnowledgeDocumentCatalog.standardMetadata());
        return latest;
    }
    public synchronized RagAnswerResult answer(RetrievalRequest request) {
        return answerWith(new ExtractiveDemoAnswerGenerator(), request);
    }
    public synchronized RagAnswerResult answerWith(RagAnswerGenerator generator, RetrievalRequest request) {
        return new RagAnswerService(retrieval, new RagPromptBuilder(), generator).answer(request);
    }
    public synchronized Map<String, Object> overview() {
        var chunks = store.findAll().stream().map(item -> Map.of(
                "chunkId", item.chunk().id(), "source", item.chunk().metadata().source(),
                "section", item.chunk().metadata().section(), "version", item.chunk().metadata().version(),
                "characters", item.chunk().text().length())).toList();
        return Map.of("report", latest, "storedChunks", store.size(), "dimensions", 64,
                "chunkSize", 256, "overlap", 32, "embedding", "DETERMINISTIC_FAKE", "chunks", chunks);
    }
    public synchronized RagAnswerResult retrievalFailure(RetrievalRequest request) {
        var unavailable = new RetrievalService(embedding,
                (vector, version, visibilities, topK) -> { throw new IllegalStateException("demo dependency failure"); },
                new CharacterBigramHybridReranker());
        return new RagAnswerService(unavailable, new RagPromptBuilder(), new ExtractiveDemoAnswerGenerator()).answer(request);
    }
}
