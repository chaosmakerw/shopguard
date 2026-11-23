package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;

import java.util.Objects;

public record ToolSelectionCase(
        String id,
        String userMessage,
        Action expectedAction,
        String expectationReason
) {
    public ToolSelectionCase {
        id = requireText(
                id,
                "评测用例ID不能为空"
        );

        userMessage = requireText(
                userMessage,
                "用户问题不能为空"
        );

        Objects.requireNonNull(
                expectedAction,
                "预期动作不能为空"
        );

        if (expectedAction == Action.UNKNOWN
                || expectedAction == Action.MULTIPLE) {
            throw new IllegalArgumentException(
                    "预期动作只能是三个已注册工具或 NONE"
            );
        }

        expectationReason = requireText(
                expectationReason,
                "预期理由不能为空"
        );
    }

    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(
                value,
                message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }
}