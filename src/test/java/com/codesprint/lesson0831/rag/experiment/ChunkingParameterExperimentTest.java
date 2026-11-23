package com.codesprint.lesson0831.rag.experiment;

import com.codesprint.lesson0831.rag.ingestion.ClasspathKnowledgeDocumentLoader;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocument;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentCatalog;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentPreprocessor;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingParameterExperimentTest {

    private static final String QUERY =
            "已发货订单不能直接取消时是否要先申请退货";

    private static final List<String> REQUIRED_PHRASES =
            List.of(
                    "已发货订单不能直接取消",
                    "先申请退货"
            );

    private static final List<String> NOISE_PHRASES =
            List.of(
                    "未发货订单",
                    "数字商品"
            );

    @Test
    void shouldCompareSmallMediumAndLargeChunksWithControlledVariables() {
        KnowledgeDocument document =
                loadRefundDocument();
        ChunkingExperimentRunner runner =
                new ChunkingExperimentRunner(
                        new KeywordEmbeddingModel()
                );

        ChunkingExperimentResult small = runner.run(
                document,
                QUERY,
                new ChunkingExperimentConfiguration(
                        "A-small",
                        24,
                        4
                ),
                1
        );
        ChunkingExperimentResult medium = runner.run(
                document,
                QUERY,
                new ChunkingExperimentConfiguration(
                        "B-medium",
                        48,
                        12
                ),
                1
        );
        ChunkingExperimentResult large = runner.run(
                document,
                QUERY,
                new ChunkingExperimentConfiguration(
                        "C-large",
                        96,
                        24
                ),
                1
        );

        assertEquals(7, small.generatedChunkCount());
        assertEquals(4, medium.generatedChunkCount());
        assertEquals(2, large.generatedChunkCount());

        assertFalse(hasCompleteAnswerSupport(small));
        assertTrue(hasCompleteAnswerSupport(medium));
        assertTrue(hasCompleteAnswerSupport(large));

        assertTrue(
                noisySnippetCount(medium)
                        < noisySnippetCount(large)
        );
    }

    @Test
    void shouldRecordActualTopKSnippetsAndScoresInDescendingOrder() {
        ChunkingExperimentRunner runner =
                new ChunkingExperimentRunner(
                        new KeywordEmbeddingModel()
                );

        ChunkingExperimentResult result = runner.run(
                loadRefundDocument(),
                QUERY,
                new ChunkingExperimentConfiguration(
                        "B-medium",
                        48,
                        12
                ),
                2
        );

        assertEquals(QUERY, result.query());
        assertEquals(2, result.topChunks().size());
        assertTrue(
                result.topChunks().get(0).score()
                        >= result.topChunks().get(1).score()
        );
        assertTrue(hasCompleteAnswerSupport(result));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.topChunks().clear()
        );
    }

    @Test
    void shouldRejectInvalidExperimentConfigurationAndRequest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkingExperimentConfiguration(
                        "A",
                        0,
                        0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkingExperimentConfiguration(
                        "A",
                        10,
                        10
                )
        );

        ChunkingExperimentRunner runner =
                new ChunkingExperimentRunner(
                        new KeywordEmbeddingModel()
                );
        ChunkingExperimentConfiguration configuration =
                new ChunkingExperimentConfiguration(
                        "B",
                        48,
                        12
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> runner.run(
                        loadRefundDocument(),
                        "   ",
                        configuration,
                        2
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> runner.run(
                        loadRefundDocument(),
                        QUERY,
                        configuration,
                        0
                )
        );
    }

    private KnowledgeDocument loadRefundDocument() {
        KnowledgeDocumentMetadata metadata =
                KnowledgeDocumentCatalog.standardMetadata()
                        .stream()
                        .filter(candidate ->
                                candidate.source().equals(
                                        "knowledge/refund-policy.md"
                                ))
                        .findFirst()
                        .orElseThrow();

        KnowledgeDocument loaded =
                new ClasspathKnowledgeDocumentLoader()
                        .load(metadata);

        return new KnowledgeDocumentPreprocessor()
                .preprocess(loaded);
    }

    private boolean hasCompleteAnswerSupport(
            ChunkingExperimentResult result
    ) {
        return result.topChunks().stream()
                .map(scored -> scored.chunk().text())
                .anyMatch(text ->
                        REQUIRED_PHRASES.stream()
                                .allMatch(text::contains));
    }

    private long noisySnippetCount(
            ChunkingExperimentResult result
    ) {
        return result.topChunks().stream()
                .map(scored -> scored.chunk().text())
                .filter(text ->
                        NOISE_PHRASES.stream()
                                .anyMatch(text::contains))
                .count();
    }

    private static class KeywordEmbeddingModel
            implements TextEmbeddingModel {

        private final List<String> keywords =
                List.of(
                        "已发货订单",
                        "不能直接取消",
                        "先申请退货",
                        "退款"
                );

        @Override
        public List<Double> embed(String text) {
            List<Double> vector =
                    new ArrayList<>(keywords.size());

            for (String keyword : keywords) {
                vector.add(
                        text.contains(keyword)
                                ? 1.0
                                : 0.0
                );
            }

            return List.copyOf(vector);
        }
    }
}
