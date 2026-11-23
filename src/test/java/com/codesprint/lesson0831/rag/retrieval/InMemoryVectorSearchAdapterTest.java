package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.EmbeddedChunk;
import com.codesprint.lesson0831.rag.ingestion.InMemoryVectorStore;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryVectorSearchAdapterTest {

    @Test
    void shouldFilterVersionAndVisibilityBeforeSimilarityCalculation() {
        VectorStore store = new InMemoryVectorStore(3);
        store.upsert(embeddedChunk(
                "eligible", "v2", DocumentVisibility.PUBLIC,
                List.of(1.0, 0.0, 0.0)
        ));

        VectorStore mismatchedStore = new VectorStoreWithExtraInvalidCandidates(
                store,
                List.of(
                        embeddedChunk(
                                "old-version", "v1", DocumentVisibility.PUBLIC,
                                List.of(1.0, 0.0)
                        ),
                        embeddedChunk(
                                "admin", "v2", DocumentVisibility.ADMIN_ONLY,
                                List.of(1.0, 0.0)
                        )
                )
        );

        VectorSearchPort adapter =
                new InMemoryVectorSearchAdapter(mismatchedStore);

        List<RetrievedChunk> results = adapter.search(
                List.of(1.0, 0.0, 0.0),
                "v2",
                Set.of(DocumentVisibility.PUBLIC),
                3
        );

        assertEquals(1, results.size());
        assertEquals("eligible", results.get(0).chunk().id());
        assertEquals(1.0, results.get(0).score(), 0.000001);
    }

    @Test
    void shouldSortByScoreThenChunkIdAndLimitTopK() {
        VectorStore store = new InMemoryVectorStore(2);
        store.upsert(embeddedChunk(
                "chunk-b", "v1", DocumentVisibility.PUBLIC,
                List.of(1.0, 0.0)
        ));
        store.upsert(embeddedChunk(
                "chunk-a", "v1", DocumentVisibility.PUBLIC,
                List.of(1.0, 0.0)
        ));
        store.upsert(embeddedChunk(
                "chunk-c", "v1", DocumentVisibility.PUBLIC,
                List.of(0.0, 1.0)
        ));

        VectorSearchPort adapter =
                new InMemoryVectorSearchAdapter(store);

        List<RetrievedChunk> results = adapter.search(
                List.of(1.0, 0.0),
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                2
        );

        assertEquals(
                List.of("chunk-a", "chunk-b"),
                results.stream()
                        .map(result -> result.chunk().id())
                        .toList()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> results.clear()
        );
    }

    @Test
    void shouldReturnEmptyListWhenNothingPassesMetadataFilters() {
        VectorStore store = new InMemoryVectorStore(2);
        store.upsert(embeddedChunk(
                "admin", "v1", DocumentVisibility.ADMIN_ONLY,
                List.of(1.0, 0.0)
        ));

        VectorSearchPort adapter =
                new InMemoryVectorSearchAdapter(store);

        List<RetrievedChunk> results = adapter.search(
                List.of(1.0, 0.0),
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                3
        );

        assertEquals(List.of(), results);
    }

    @Test
    void shouldReturnZeroScoreForZeroVector() {
        VectorStore store = new InMemoryVectorStore(2);
        store.upsert(embeddedChunk(
                "zero", "v1", DocumentVisibility.PUBLIC,
                List.of(0.0, 0.0)
        ));

        VectorSearchPort adapter =
                new InMemoryVectorSearchAdapter(store);

        List<RetrievedChunk> results = adapter.search(
                List.of(1.0, 0.0),
                "v1",
                Set.of(DocumentVisibility.PUBLIC),
                1
        );

        assertEquals(0.0, results.get(0).score(), 0.000001);
    }

    @Test
    void shouldRejectInvalidSearchArgumentsAndDimensions() {
        VectorStore store = new InMemoryVectorStore(2);
        store.upsert(embeddedChunk(
                "chunk-1", "v1", DocumentVisibility.PUBLIC,
                List.of(1.0, 0.0)
        ));
        VectorSearchPort adapter =
                new InMemoryVectorSearchAdapter(store);

        assertThrows(
                NullPointerException.class,
                () -> new InMemoryVectorSearchAdapter(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> adapter.search(null, "v1", Set.of(DocumentVisibility.PUBLIC), 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.search(List.of(), "v1", Set.of(DocumentVisibility.PUBLIC), 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.search(
                        Arrays.asList(1.0, null),
                        "v1",
                        Set.of(DocumentVisibility.PUBLIC),
                        1
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.search(List.of(1.0), "v1", Set.of(DocumentVisibility.PUBLIC), 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.search(List.of(1.0, 0.0), "v1", Set.of(), 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.search(
                        List.of(1.0, 0.0),
                        "v1",
                        Set.of(DocumentVisibility.PUBLIC),
                        0
                )
        );
    }

    private EmbeddedChunk embeddedChunk(
            String id,
            String version,
            DocumentVisibility visibility,
            List<Double> vector
    ) {
        return new EmbeddedChunk(
                new KnowledgeChunk(
                        id,
                        0,
                        "规则-" + id,
                        new KnowledgeDocumentMetadata(
                                "knowledge/" + id + ".md",
                                "section",
                                version,
                                visibility
                        )
                ),
                vector
        );
    }

    private record VectorStoreWithExtraInvalidCandidates(
            VectorStore delegate,
            List<EmbeddedChunk> extraCandidates
    ) implements VectorStore {

        @Override
        public com.codesprint.lesson0831.rag.ingestion.VectorWriteStatus upsert(
                EmbeddedChunk chunk
        ) {
            return delegate.upsert(chunk);
        }

        @Override
        public com.codesprint.lesson0831.rag.ingestion.VectorWriteSummary upsertAll(
                List<EmbeddedChunk> chunks
        ) {
            return delegate.upsertAll(chunks);
        }

        @Override
        public java.util.Optional<EmbeddedChunk> findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public List<EmbeddedChunk> findAll() {
            java.util.ArrayList<EmbeddedChunk> all =
                    new java.util.ArrayList<>(delegate.findAll());
            all.addAll(extraCandidates);
            return List.copyOf(all);
        }

        @Override
        public int size() {
            return delegate.size() + extraCandidates.size();
        }
    }
}
