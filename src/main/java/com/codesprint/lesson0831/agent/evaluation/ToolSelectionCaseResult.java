package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Result;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;

import java.util.Objects;

public record ToolSelectionCaseResult(
        ToolSelectionCase testCase,
        ModelTurn modelTurn,
        Result result
) {
    public ToolSelectionCaseResult {
        Objects.requireNonNull(
                testCase,
                "评测用例不能为空"
        );

        Objects.requireNonNull(
                modelTurn,
                "模型响应不能为空"
        );

        Objects.requireNonNull(
                result,
                "判定结果不能为空"
        );

        if (testCase.expectedAction()
                != result.expected()) {
            throw new IllegalArgumentException(
                    "评测用例与判定结果的预期动作不一致"
            );
        }
    }
}