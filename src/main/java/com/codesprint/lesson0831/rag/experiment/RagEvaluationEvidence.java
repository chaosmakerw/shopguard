package com.codesprint.lesson0831.rag.experiment;

public record RagEvaluationEvidence(
        boolean sourceContainsAnswer,
        boolean chunksPreserveAnswer,
        boolean retrievalContainsAnswer,
        boolean generatedAnswerCorrect
) {
}
