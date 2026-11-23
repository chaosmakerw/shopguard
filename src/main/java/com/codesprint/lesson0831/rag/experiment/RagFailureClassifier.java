package com.codesprint.lesson0831.rag.experiment;

import java.util.Objects;

public class RagFailureClassifier {

    public RagFailureType classify(
            RagEvaluationEvidence evidence
    ) {
        Objects.requireNonNull(
                evidence,
                "RAG评测证据不能为空"
        );

        if (!evidence.sourceContainsAnswer()) {
            return RagFailureType
                    .SOURCE_KNOWLEDGE_MISSING;
        }

        if (!evidence.chunksPreserveAnswer()) {
            return RagFailureType
                    .CHUNKING_FAILURE;
        }

        if (!evidence.retrievalContainsAnswer()) {
            return RagFailureType
                    .RETRIEVAL_FAILURE;
        }

        if (!evidence.generatedAnswerCorrect()) {
            return RagFailureType
                    .GENERATION_FAILURE;
        }

        return RagFailureType.NONE;
    }
}
