package com.codesprint.lesson0831.rag.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagFailureClassifierTest {

    private final RagFailureClassifier classifier =
            new RagFailureClassifier();

    @Test
    void shouldClassifyMissingSourceKnowledgeFirst() {
        assertEquals(
                RagFailureType.SOURCE_KNOWLEDGE_MISSING,
                classifier.classify(
                        new RagEvaluationEvidence(
                                false,
                                false,
                                false,
                                false
                        )
                )
        );
    }

    @Test
    void shouldClassifyChunkingFailureWhenSourceHasAnswerButChunksBreakIt() {
        assertEquals(
                RagFailureType.CHUNKING_FAILURE,
                classifier.classify(
                        new RagEvaluationEvidence(
                                true,
                                false,
                                false,
                                false
                        )
                )
        );
    }

    @Test
    void shouldClassifyRetrievalFailureWhenCompleteChunkMissesTopK() {
        assertEquals(
                RagFailureType.RETRIEVAL_FAILURE,
                classifier.classify(
                        new RagEvaluationEvidence(
                                true,
                                true,
                                false,
                                false
                        )
                )
        );
    }

    @Test
    void shouldClassifyGenerationFailureWhenContextIsCorrectButAnswerIsWrong() {
        assertEquals(
                RagFailureType.GENERATION_FAILURE,
                classifier.classify(
                        new RagEvaluationEvidence(
                                true,
                                true,
                                true,
                                false
                        )
                )
        );
    }

    @Test
    void shouldReturnNoneWhenEveryStageIsCorrectAndRejectMissingEvidence() {
        assertEquals(
                RagFailureType.NONE,
                classifier.classify(
                        new RagEvaluationEvidence(
                                true,
                                true,
                                true,
                                true
                        )
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> classifier.classify(null)
        );
    }
}
