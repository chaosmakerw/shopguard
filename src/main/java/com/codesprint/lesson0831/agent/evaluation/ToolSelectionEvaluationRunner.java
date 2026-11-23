package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Result;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ToolSelectionEvaluationRunner {

    private static final String EVALUATION_USER_ID =
            "tool-selection-evaluation-user";

    private static final String SESSION_ID_PREFIX =
            "tool-selection-evaluation-";

    private final ChatModel model;
    private final ToolSelectionEvaluation evaluation;
    private final ToolSelectionReportCalculator calculator;

    public ToolSelectionEvaluationRunner(
            ChatModel model,
            ToolSelectionEvaluation evaluation,
            ToolSelectionReportCalculator calculator
    ) {
        this.model = Objects.requireNonNull(
                model,
                "模型不能为空"
        );

        this.evaluation = Objects.requireNonNull(
                evaluation,
                "单条评测器不能为空"
        );

        this.calculator = Objects.requireNonNull(
                calculator,
                "统计器不能为空"
        );
    }

    public ToolSelectionEvaluationReport run(
            List<ToolSelectionCase> cases
    ) {
        Objects.requireNonNull(
                cases,
                "评测用例列表不能为空"
        );

        List<ToolSelectionCase> safeCases =
                List.copyOf(cases);

        if (safeCases.isEmpty()) {
            throw new IllegalArgumentException(
                    "评测用例列表不能为空"
            );
        }

        List<Result> results =
                new ArrayList<>();

        List<ToolSelectionCaseResult> caseResults =
                new ArrayList<>();

        for (ToolSelectionCase testCase : safeCases) {
            ChatRequest request =
                    new ChatRequest(
                            EVALUATION_USER_ID,
                            SESSION_ID_PREFIX
                                    + testCase.id(),
                            testCase.userMessage()
                    );

            ModelInput input =
                    new ModelInput(
                            request,
                            List.of(),
                            List.of()
                    );

            ModelTurn turn =
                    model.nextTurn(input);

            Result result =
                    evaluation.evaluate(
                            testCase.expectedAction(),
                            turn
                    );

            results.add(result);

            caseResults.add(
                    new ToolSelectionCaseResult(
                            testCase,
                            turn,
                            result
                    )
            );
        }

        ToolSelectionMetrics metrics =
                calculator.calculate(results);

        return new ToolSelectionEvaluationReport(
                caseResults,
                metrics
        );
    }
}