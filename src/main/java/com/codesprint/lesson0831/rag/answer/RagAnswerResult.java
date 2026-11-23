package com.codesprint.lesson0831.rag.answer;

import java.util.List;
import java.util.Objects;

public record RagAnswerResult(
        boolean success,
        RagAnswerCode code,
        String answer,
        List<RagCitation> citations
) {

    public RagAnswerResult {
        Objects.requireNonNull(code, "回答结果码不能为空");
        answer = requireText(answer, "回答文本不能为空");
        Objects.requireNonNull(citations, "引用列表不能为空");

        citations = List.copyOf(citations);

        if (code == RagAnswerCode.ANSWERED) {
            if (!success || citations.isEmpty()) {
                throw new IllegalArgumentException(
                        "ANSWERED必须成功且包含引用"
                );
            }
        } else {
            if (!citations.isEmpty()) {
                throw new IllegalArgumentException(
                        "非回答结果不能包含引用"
                );
            }

            if (code == RagAnswerCode.INSUFFICIENT_EVIDENCE
                    && !success) {
                throw new IllegalArgumentException(
                        "资料不足属于正常完成的结果"
                );
            }

            if ((code == RagAnswerCode.RETRIEVAL_UNAVAILABLE
                    || code == RagAnswerCode.GENERATION_UNAVAILABLE)
                    && success) {
                throw new IllegalArgumentException(
                        "依赖故障不能标记为成功"
                );
            }
        }
    }

    public static RagAnswerResult answered(
            String answer,
            List<RagCitation> citations
    ) {
        return new RagAnswerResult(
                true,
                RagAnswerCode.ANSWERED,
                answer,
                citations
        );
    }

    public static RagAnswerResult insufficientEvidence() {
        return new RagAnswerResult(
                true,
                RagAnswerCode.INSUFFICIENT_EVIDENCE,
                "现有资料中没有足够信息，无法回答。",
                List.of()
        );
    }

    public static RagAnswerResult retrievalUnavailable() {
        return new RagAnswerResult(
                false,
                RagAnswerCode.RETRIEVAL_UNAVAILABLE,
                "知识检索暂时不可用。",
                List.of()
        );
    }

    public static RagAnswerResult generationUnavailable() {
        return new RagAnswerResult(
                false,
                RagAnswerCode.GENERATION_UNAVAILABLE,
                "回答生成暂时不可用。",
                List.of()
        );
    }

    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}