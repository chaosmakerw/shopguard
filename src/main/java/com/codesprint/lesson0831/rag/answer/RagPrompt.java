package com.codesprint.lesson0831.rag.answer;

import java.util.Objects;

public record RagPrompt(
        String systemInstruction,
        String question,
        String context
) {

    public RagPrompt {
        systemInstruction = requireText(
                systemInstruction,
                "系统指令不能为空"
        );
        question = requireText(
                question,
                "用户问题不能为空"
        );
        context = requireText(
                context,
                "检索上下文不能为空"
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
