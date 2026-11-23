package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionBoundaryClassificationTest {

    @Test
    void shouldClassifyEmptyDocumentAsValidationFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocument(
                        exampleMetadata(),
                        " \n\t "
                )
        );
    }

    @Test
    void shouldRejectBadFormatBeforeEmbeddingCanRun() {
        KnowledgeDocument invalidDocument =
                new KnowledgeDocument(
                        exampleMetadata(),
                        "正常内容\u0000隐藏乱码"
                );
        KnowledgeDocumentFormatValidator validator =
                new KnowledgeDocumentFormatValidator();
        AtomicInteger embeddingCalls =
                new AtomicInteger();
        KnowledgeChunkEmbedder embedder =
                new KnowledgeChunkEmbedder(text -> {
                    embeddingCalls.incrementAndGet();
                    return List.of(1.0, 0.0);
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    validator.validate(invalidDocument);
                    embedder.embed(
                            new KnowledgeChunk(
                                    "bad::v1::0",
                                    0,
                                    invalidDocument.content(),
                                    invalidDocument.metadata()
                            )
                    );
                }
        );

        assertEquals(0, embeddingCalls.get());
    }

    @Test
    void shouldAcceptNormalLineBreaksAndTabs() {
        KnowledgeDocument document =
                new KnowledgeDocument(
                        exampleMetadata(),
                        "规则标题\r\n\t规则正文"
                );

        assertDoesNotThrow(
                () -> new KnowledgeDocumentFormatValidator()
                        .validate(document)
        );
    }

    @Test
    void shouldTreatDuplicateSourceAndVersionAsUnchangedInsteadOfNewCopies() {
        KnowledgeDocumentMetadata metadata =
                KnowledgeDocumentCatalog.standardMetadata()
                        .stream()
                        .filter(candidate ->
                                candidate.source().equals(
                                        "knowledge/refund-policy.md"
                                ))
                        .findFirst()
                        .orElseThrow();
        InMemoryVectorStore store =
                new InMemoryVectorStore(8);
        KnowledgeIngestionService service =
                new KnowledgeIngestionService(
                        new ClasspathKnowledgeDocumentLoader(),
                        new KnowledgeDocumentFormatValidator(),
                        new KnowledgeDocumentPreprocessor(),
                        new KnowledgeDocumentChunker(48, 12),
                        new KnowledgeChunkEmbedder(
                                new DeterministicFakeEmbeddingModel(8)
                        ),
                        store
                );

        IngestionReport report = service.ingest(
                List.of(metadata, metadata)
        );

        assertEquals(2, report.documentCount());
        assertEquals(report.created(), report.unchanged());
        assertEquals(0, report.updated());
        assertEquals(report.created(), store.size());
        assertEquals(report.chunkCount(), report.total());
    }

    private KnowledgeDocumentMetadata exampleMetadata() {
        return new KnowledgeDocumentMetadata(
                "knowledge/example.md",
                "example",
                "v1",
                DocumentVisibility.PUBLIC
        );
    }
}
