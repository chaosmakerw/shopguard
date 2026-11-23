package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentChunkerTest {

    @Test
    void shouldSplitDocumentWithConfiguredSizeAndOverlap() {
        KnowledgeDocument document = exampleDocument(
                "ABCDEFGHIJK"
        );
        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(6, 2);

        List<KnowledgeChunk> chunks =
                chunker.chunk(document);

        assertEquals(3, chunks.size());
        assertChunk(
                chunks.get(0),
                "knowledge/example.md::v1::0",
                0,
                "ABCDEF",
                document.metadata()
        );
        assertChunk(
                chunks.get(1),
                "knowledge/example.md::v1::1",
                1,
                "EFGHIJ",
                document.metadata()
        );
        assertChunk(
                chunks.get(2),
                "knowledge/example.md::v1::2",
                2,
                "IJK",
                document.metadata()
        );
    }

    @Test
    void shouldProduceOneChunkWhenDocumentIsShorterThanChunkSize() {
        KnowledgeDocument document = exampleDocument(
                "短文档"
        );
        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(20, 5);

        List<KnowledgeChunk> chunks =
                chunker.chunk(document);

        assertEquals(1, chunks.size());
        assertEquals("短文档", chunks.get(0).text());
        assertEquals(0, chunks.get(0).index());
    }

    @Test
    void shouldChunkAllRealDocumentsAndReturnImmutableResults() {
        ClasspathKnowledgeDocumentLoader loader =
                new ClasspathKnowledgeDocumentLoader();
        KnowledgeDocumentPreprocessor preprocessor =
                new KnowledgeDocumentPreprocessor();
        List<KnowledgeDocument> documents =
                preprocessor.preprocessAll(
                        loader.loadAll(
                                KnowledgeDocumentCatalog
                                        .standardMetadata()
                        )
                );
        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(80, 20);

        List<KnowledgeChunk> chunks =
                chunker.chunkAll(documents);

        assertTrue(chunks.size() > documents.size());
        assertTrue(chunks.stream()
                .allMatch(chunk ->
                        !chunk.text().isBlank()
                                && chunk.text().length() <= 80));
        assertTrue(chunks.stream()
                .anyMatch(chunk ->
                        chunk.metadata().visibility()
                                == DocumentVisibility.ADMIN_ONLY));
        assertThrows(
                UnsupportedOperationException.class,
                () -> chunks.clear()
        );
    }

    @Test
    void shouldGenerateSameChunksForSameDocumentAndConfiguration() {
        KnowledgeDocument document = exampleDocument(
                "ABCDEFGHIJK"
        );
        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(6, 2);

        List<KnowledgeChunk> firstResult =
                chunker.chunk(document);
        List<KnowledgeChunk> secondResult =
                chunker.chunk(document);

        assertEquals(firstResult, secondResult);
    }

    @Test
    void shouldRejectInvalidChunkingConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentChunker(0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentChunker(10, -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentChunker(10, 10)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentChunker(10, 11)
        );
    }

    @Test
    void shouldRejectMissingDocumentsAndInvalidChunks() {
        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(10, 2);

        assertThrows(
                NullPointerException.class,
                () -> chunker.chunk(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> chunker.chunkAll(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunkAll(List.of())
        );
        assertThrows(
                NullPointerException.class,
                () -> chunker.chunkAll(
                        Arrays.asList(
                                exampleDocument("有效文档"),
                                null
                        )
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeChunk(
                        " ",
                        0,
                        "有效文本",
                        exampleDocument("正文").metadata()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeChunk(
                        "chunk-1",
                        -1,
                        "有效文本",
                        exampleDocument("正文").metadata()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeChunk(
                        "chunk-1",
                        0,
                        " ",
                        exampleDocument("正文").metadata()
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new KnowledgeChunk(
                        "chunk-1",
                        0,
                        "有效文本",
                        null
                )
        );
    }

    private KnowledgeDocument exampleDocument(
            String content
    ) {
        return new KnowledgeDocument(
                new KnowledgeDocumentMetadata(
                        "knowledge/example.md",
                        "example",
                        "v1",
                        DocumentVisibility.PUBLIC
                ),
                content
        );
    }

    private void assertChunk(
            KnowledgeChunk chunk,
            String expectedId,
            int expectedIndex,
            String expectedText,
            KnowledgeDocumentMetadata expectedMetadata
    ) {
        assertEquals(expectedId, chunk.id());
        assertEquals(expectedIndex, chunk.index());
        assertEquals(expectedText, chunk.text());
        assertSame(expectedMetadata, chunk.metadata());
    }
}
