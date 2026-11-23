package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.ErrorType;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSelectionStandardMatrixIntegrationTest {

    @Test
    void shouldEvaluateAllTwelveStandardCases() {
        MatrixScriptedModel model =
                new MatrixScriptedModel(false);

        ToolSelectionEvaluationReport report =
                newRunner(model).run(
                        ToolSelectionMatrix.standardCases()
                );

        ToolSelectionMetrics metrics = report.metrics();

        assertEquals(12, metrics.total());
        assertEquals(12, metrics.correct());
        assertEquals(1.0, metrics.accuracy(), 0.000001);
        assertEquals(Map.of(), metrics.errorCounts());
        assertEquals(12, report.caseResults().size());
        assertEquals(12, model.inputs().size());

        for (Action action : List.of(
                Action.SEARCH_PRODUCTS,
                Action.GET_PRODUCT_DETAIL,
                Action.LIST_MY_ORDERS,
                Action.NONE
        )) {
            ToolSelectionMetrics.ActionMetrics actionMetrics =
                    metrics.byAction().get(action);

            assertEquals(3, actionMetrics.total());
            assertEquals(3, actionMetrics.correct());
            assertEquals(
                    1.0,
                    actionMetrics.accuracy(),
                    0.000001
            );
        }

        assertTrue(
                report.caseResults()
                        .stream()
                        .allMatch(caseResult ->
                                caseResult.result().correct())
        );
    }

    @Test
    void shouldLocateOveruseOnToolNameBoundaryCase() {
        MatrixScriptedModel model =
                new MatrixScriptedModel(true);

        ToolSelectionEvaluationReport report =
                newRunner(model).run(
                        ToolSelectionMatrix.standardCases()
                );

        assertEquals(12, report.metrics().total());
        assertEquals(11, report.metrics().correct());
        assertEquals(
                11.0 / 12.0,
                report.metrics().accuracy(),
                0.000001
        );
        assertEquals(
                Map.of(ErrorType.TOOL_OVERUSE, 1),
                report.metrics().errorCounts()
        );

        List<ToolSelectionCaseResult> failures =
                report.caseResults()
                        .stream()
                        .filter(caseResult ->
                                !caseResult.result().correct())
                        .toList();

        assertEquals(1, failures.size());

        ToolSelectionCaseResult failure = failures.get(0);

        assertEquals(
                "none-tool-name",
                failure.testCase().id()
        );
        assertEquals(Action.NONE, failure.result().expected());
        assertEquals(
                Action.SEARCH_PRODUCTS,
                failure.result().actual()
        );
        assertEquals(
                ErrorType.TOOL_OVERUSE,
                failure.result().errorType()
        );
        assertEquals(
                "search_products",
                failure.modelTurn()
                        .toolCalls()
                        .get(0)
                        .name()
        );
    }

    private ToolSelectionEvaluationRunner newRunner(
            ChatModel model
    ) {
        return new ToolSelectionEvaluationRunner(
                model,
                new ToolSelectionEvaluation(),
                new ToolSelectionReportCalculator()
        );
    }

    private static ModelTurn toolTurn(String toolName) {
        return new ModelTurn(
                null,
                List.of(
                        new ToolCall(
                                "call-1",
                                toolName,
                                Map.of()
                        )
                )
        );
    }

    private static ModelTurn textTurn() {
        return new ModelTurn(
                "模型直接回答",
                List.of()
        );
    }

    private static final class MatrixScriptedModel
            implements ChatModel {

        private final boolean overuseToolNameBoundary;
        private final List<ModelInput> inputs =
                new ArrayList<>();

        private MatrixScriptedModel(
                boolean overuseToolNameBoundary
        ) {
            this.overuseToolNameBoundary =
                    overuseToolNameBoundary;
        }

        @Override
        public String chat(
                ChatRequest request,
                List<ChatMessage> history
        ) {
            throw new UnsupportedOperationException(
                    "标准矩阵评测必须调用 nextTurn"
            );
        }

        @Override
        public ModelTurn nextTurn(ModelInput input) {
            inputs.add(input);

            String question =
                    input.request().question();

            if (question.contains("search_products")) {
                if (overuseToolNameBoundary) {
                    return toolTurn("search_products");
                }

                return textTurn();
            }

            if (question.contains("你好")
                    || question.contains("继承是什么")) {
                return textTurn();
            }

            if (question.contains("我的订单")
                    || question.contains("未取消订单")
                    || question.contains("买过哪些商品")) {
                return toolTurn("list_my_orders");
            }

            if (question.contains("P1001")
                    || question.contains("P1002")
                    || question.contains("P1003")) {
                return toolTurn("get_product_detail");
            }

            return toolTurn("search_products");
        }

        private List<ModelInput> inputs() {
            return List.copyOf(inputs);
        }
    }
}
