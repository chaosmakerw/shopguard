package com.codesprint.lesson0831.rag.ingestion;

import java.util.List;
import java.util.Objects;

public class KnowledgeIngestionService {

    private final ClasspathKnowledgeDocumentLoader loader;
    private final KnowledgeDocumentFormatValidator formatValidator;
    private final KnowledgeDocumentPreprocessor preprocessor;
    private final KnowledgeDocumentChunker chunker;
    private final KnowledgeChunkEmbedder embedder;
    private final VectorStore vectorStore;

    public KnowledgeIngestionService(
            ClasspathKnowledgeDocumentLoader loader,
            KnowledgeDocumentPreprocessor preprocessor,
            KnowledgeDocumentChunker chunker,
            KnowledgeChunkEmbedder embedder,
            VectorStore vectorStore
    ) {
        this(
                loader,
                new KnowledgeDocumentFormatValidator(),
                preprocessor,
                chunker,
                embedder,
                vectorStore
        );
    }

    public KnowledgeIngestionService(
            ClasspathKnowledgeDocumentLoader loader,
            KnowledgeDocumentFormatValidator formatValidator,
            KnowledgeDocumentPreprocessor preprocessor,
            KnowledgeDocumentChunker chunker,
            KnowledgeChunkEmbedder embedder,
            VectorStore vectorStore
    ) {
        this.loader = Objects.requireNonNull(
                loader,
                "文档加载器不能为空"
        );
        this.formatValidator = Objects.requireNonNull(
                formatValidator,
                "文档格式校验器不能为空"
        );
        this.preprocessor = Objects.requireNonNull(
                preprocessor,
                "文档预处理器不能为空"
        );
        this.chunker = Objects.requireNonNull(
                chunker,
                "文档切分器不能为空"
        );
        this.embedder = Objects.requireNonNull(
                embedder,
                "Chunk向量化器不能为空"
        );
        this.vectorStore = Objects.requireNonNull(
                vectorStore,
                "向量存储不能为空"
        );
    }

    public IngestionReport ingest(
            List<KnowledgeDocumentMetadata> metadataList
    ) {
        Objects.requireNonNull(
                metadataList,
                "文档元数据列表不能为空"
        );

        if (metadataList.isEmpty()) {
            throw new IllegalArgumentException(
                    "文档元数据列表不能为空"
            );
        }

        List<KnowledgeDocumentMetadata> sourceMetadata =
                List.copyOf(metadataList);

        List<KnowledgeDocument> loadedDocuments =
                loader.loadAll(sourceMetadata);

        List<KnowledgeDocument> validatedDocuments =
                formatValidator.validateAll(
                        loadedDocuments
                );

        List<KnowledgeDocument> preprocessedDocuments =
                preprocessor.preprocessAll(
                        validatedDocuments
                );

        List<KnowledgeChunk> chunks =
                chunker.chunkAll(
                        preprocessedDocuments
                );

        List<EmbeddedChunk> embeddedChunks =
                embedder.embedAll(chunks);

        VectorWriteSummary writeSummary =
                vectorStore.upsertAll(
                        embeddedChunks
                );

        return new IngestionReport(
                loadedDocuments.size(),
                chunks.size(),
                writeSummary.created(),
                writeSummary.updated(),
                writeSummary.unchanged(),
                0
        );
    }

}
