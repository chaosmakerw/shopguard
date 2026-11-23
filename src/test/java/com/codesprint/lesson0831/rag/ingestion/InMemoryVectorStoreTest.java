package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryVectorStoreTest {

    @Test
    void shouldCreateAndReadVectorByStableChunkId() {
        VectorStore store =
                new InMemoryVectorStore(2);
        EmbeddedChunk chunk = embeddedChunk(
                "chunk-1",
                "订单规则",
                List.of(1.0, 0.0)
        );

        VectorWriteStatus status =
                store.upsert(chunk);

        assertEquals(VectorWriteStatus.CREATED, status);
        assertEquals(1, store.size());
        assertEquals(
                chunk,
                store.findById("chunk-1").orElseThrow()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> store.findAll().clear()
        );
    }

    @Test
    void shouldKeepSizeAndReportUnchangedForIdenticalChunk() {
        VectorStore store =
                new InMemoryVectorStore(2);
        EmbeddedChunk chunk = embeddedChunk(
                "chunk-1",
                "订单规则",
                List.of(1.0, 0.0)
        );

        store.upsert(chunk);
        VectorWriteStatus secondStatus =
                store.upsert(chunk);

        assertEquals(
                VectorWriteStatus.UNCHANGED,
                secondStatus
        );
        assertEquals(1, store.size());
    }

    @Test
    void shouldReplaceExistingValueAndReportUpdated() {
        VectorStore store =
                new InMemoryVectorStore(2);
        store.upsert(embeddedChunk(
                "chunk-1",
                "旧规则",
                List.of(1.0, 0.0)
        ));
        EmbeddedChunk updated = embeddedChunk(
                "chunk-1",
                "新规则",
                List.of(0.5, 0.5)
        );

        VectorWriteStatus status =
                store.upsert(updated);

        assertEquals(VectorWriteStatus.UPDATED, status);
        assertEquals(1, store.size());
        assertEquals(
                updated,
                store.findById("chunk-1").orElseThrow()
        );
    }

    @Test
    void shouldSummarizeCreatedUpdatedAndUnchangedWrites() {
        VectorStore store =
                new InMemoryVectorStore(2);
        EmbeddedChunk unchanged = embeddedChunk(
                "chunk-1",
                "订单规则",
                List.of(1.0, 0.0)
        );
        store.upsert(unchanged);
        store.upsert(embeddedChunk(
                "chunk-2",
                "旧退款规则",
                List.of(0.0, 1.0)
        ));

        VectorWriteSummary summary =
                store.upsertAll(List.of(
                        unchanged,
                        embeddedChunk(
                                "chunk-2",
                                "新退款规则",
                                List.of(0.5, 0.5)
                        ),
                        embeddedChunk(
                                "chunk-3",
                                "安全规则",
                                List.of(0.25, 0.75)
                        )
                ));

        assertEquals(
                new VectorWriteSummary(1, 1, 1),
                summary
        );
        assertEquals(3, summary.total());
        assertEquals(3, store.size());
    }

    @Test
    void shouldRejectWholeBatchBeforeWritingWhenDimensionIsWrong() {
        VectorStore store =
                new InMemoryVectorStore(2);
        store.upsert(embeddedChunk(
                "chunk-1",
                "已有规则",
                List.of(1.0, 0.0)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> store.upsertAll(List.of(
                        embeddedChunk(
                                "chunk-2",
                                "本来合法",
                                List.of(0.5, 0.5)
                        ),
                        embeddedChunk(
                                "chunk-3",
                                "错误维度",
                                List.of(0.2, 0.3, 0.5)
                        )
                ))
        );

        assertEquals(1, store.size());
        assertFalse(store.findById("chunk-2").isPresent());
    }

    @Test
    void shouldRejectInvalidConfigurationAndInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InMemoryVectorStore(0)
        );

        VectorStore store =
                new InMemoryVectorStore(2);

        assertThrows(
                NullPointerException.class,
                () -> store.upsert(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> store.upsertAll(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> store.upsertAll(List.of())
        );
        assertThrows(
                NullPointerException.class,
                () -> store.upsertAll(
                        Arrays.asList(
                                embeddedChunk(
                                        "chunk-1",
                                        "规则",
                                        List.of(1.0, 0.0)
                                ),
                                null
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> store.findById("   ")
        );
        assertFalse(store.findById("missing").isPresent());
    }

    private EmbeddedChunk embeddedChunk(
            String id,
            String text,
            List<Double> vector
    ) {
        return new EmbeddedChunk(
                new KnowledgeChunk(
                        id,
                        0,
                        text,
                        new KnowledgeDocumentMetadata(
                                "knowledge/example.md",
                                "example",
                                "v1",
                                DocumentVisibility.PUBLIC
                        )
                ),
                vector
        );
    }
}
