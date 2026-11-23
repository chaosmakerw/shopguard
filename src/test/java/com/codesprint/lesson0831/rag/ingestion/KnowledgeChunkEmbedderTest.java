package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeChunkEmbedderTest {

    @Test
    void shouldGenerateDeterministicNormalizedVectorWithConfiguredDimension() {
        DeterministicFakeEmbeddingModel model =
                new DeterministicFakeEmbeddingModel(4);

        List<Double> firstVector = model.embed("ABC");
        List<Double> secondVector = model.embed("ABC");
        List<Double> differentVector = model.embed("XYZ");

        assertEquals(4, firstVector.size());
        assertEquals(firstVector, secondVector);
        assertNotEquals(firstVector, differentVector);

        double squaredLength = firstVector.stream()
                .mapToDouble(value -> value * value)
                .sum();
        assertEquals(1.0, squaredLength, 0.000001);

        assertThrows(
                UnsupportedOperationException.class,
                () -> firstVector.set(0, 100.0)
        );
    }

    @Test
    void shouldPassChunkTextToEmbeddingModelAndKeepOriginalChunk() {
        AtomicReference<String> receivedText =
                new AtomicReference<>();

        TextEmbeddingModel model = text -> {
            receivedText.set(text);
            return List.of(0.25, 0.75);
        };

        KnowledgeChunkEmbedder embedder =
                new KnowledgeChunkEmbedder(model);
        KnowledgeChunk chunk = exampleChunk();

        EmbeddedChunk result = embedder.embed(chunk);

        assertEquals(chunk.text(), receivedText.get());
        assertSame(chunk, result.chunk());
        assertEquals(List.of(0.25, 0.75), result.vector());
    }

    @Test
    void shouldEmbedAllRealChunksWithFakeModel() {
        ClasspathKnowledgeDocumentLoader loader =
                new ClasspathKnowledgeDocumentLoader();
        KnowledgeDocumentPreprocessor preprocessor =
                new KnowledgeDocumentPreprocessor();
        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(80, 20);
        List<KnowledgeChunk> chunks =
                chunker.chunkAll(
                        preprocessor.preprocessAll(
                                loader.loadAll(
                                        KnowledgeDocumentCatalog
                                                .standardMetadata()
                                )
                        )
                );
        KnowledgeChunkEmbedder embedder =
                new KnowledgeChunkEmbedder(
                        new DeterministicFakeEmbeddingModel(8)
                );

        List<EmbeddedChunk> results =
                embedder.embedAll(chunks);

        assertEquals(chunks.size(), results.size());
        assertFalse(results.isEmpty());
        assertEquals(8, results.get(0).vector().size());
        assertEquals(chunks.get(0), results.get(0).chunk());
        assertThrows(
                UnsupportedOperationException.class,
                () -> results.clear()
        );
    }

    @Test
    void shouldRejectInvalidEmbeddingInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeterministicFakeEmbeddingModel(0)
        );

        DeterministicFakeEmbeddingModel model =
                new DeterministicFakeEmbeddingModel(4);
        assertThrows(
                NullPointerException.class,
                () -> model.embed(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> model.embed("   ")
        );
    }

    @Test
    void shouldRejectInvalidEmbeddedChunk() {
        KnowledgeChunk chunk = exampleChunk();

        assertThrows(
                NullPointerException.class,
                () -> new EmbeddedChunk(null, List.of(1.0))
        );
        assertThrows(
                NullPointerException.class,
                () -> new EmbeddedChunk(chunk, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedChunk(chunk, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedChunk(
                        chunk,
                        List.of(Double.NaN)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedChunk(
                        chunk,
                        List.of(Double.POSITIVE_INFINITY)
                )
        );
    }

    @Test
    void shouldRejectMissingOrEmptyChunkListBeforeEmbedding() {
        KnowledgeChunkEmbedder embedder =
                new KnowledgeChunkEmbedder(
                        new DeterministicFakeEmbeddingModel(4)
                );

        assertThrows(
                NullPointerException.class,
                () -> new KnowledgeChunkEmbedder(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> embedder.embed(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> embedder.embedAll(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> embedder.embedAll(List.of())
        );
        assertThrows(
                NullPointerException.class,
                () -> embedder.embedAll(
                        Arrays.asList(
                                exampleChunk(),
                                null
                        )
                )
        );
    }

    private KnowledgeChunk exampleChunk() {
        return new KnowledgeChunk(
                "knowledge/example.md::v1::0",
                0,
                "订单创建后30分钟内未支付会自动关闭。",
                new KnowledgeDocumentMetadata(
                        "knowledge/example.md",
                        "order-lifecycle",
                        "v1",
                        DocumentVisibility.AUTHENTICATED
                )
        );
    }
}
