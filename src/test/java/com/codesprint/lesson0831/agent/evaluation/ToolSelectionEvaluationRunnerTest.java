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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSelectionEvaluationRunnerTest {

    private final ToolSelectionEvaluation evaluation =
            new ToolSelectionEvaluation();

    private final ToolSelectionReportCalculator calculator =
            new ToolSelectionReportCalculator();

    @Test
    void shouldEvaluateEveryCaseWithAnIsolatedModelInput() {
        List<ToolSelectionCase> cases = fourActionCases();

        RecordingScriptedModel model =
                new RecordingScriptedModel(
                        List.of(
                                toolTurn("search_products"),
                                toolTurn("get_product_detail"),
                                toolTurn("list_my_orders"),
                                textTurn()
                        )
                );

        ToolSelectionEvaluationRunner runner =
                new ToolSelectionEvaluationRunner(
                        model,
                        evaluation,
                        calculator
                );

        ToolSelectionEvaluationReport report =
                runner.run(cases);

        ToolSelectionMetrics metrics = report.metrics();

        assertEquals(4, metrics.total());
        assertEquals(4, metrics.correct());
        assertEquals(1.0, metrics.accuracy(), 0.000001);
        assertEquals(Map.of(), metrics.errorCounts());

        assertEquals(4, report.caseResults().size());

        for (int index = 0; index < cases.size(); index++) {
            ToolSelectionCaseResult caseResult =
                    report.caseResults().get(index);

            assertEquals(cases.get(index), caseResult.testCase());
            assertEquals(
                    model.turnAt(index),
                    caseResult.modelTurn()
            );
            assertTrue(caseResult.result().correct());
            assertEquals(
                    cases.get(index).expectedAction(),
                    caseResult.result().expected()
            );
        }

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.caseResults().clear()
        );

        assertEquals(4, model.inputs().size());

        for (int index = 0; index < cases.size(); index++) {
            ToolSelectionCase testCase = cases.get(index);
            ModelInput input = model.inputs().get(index);

            assertEquals(
                    "tool-selection-evaluation-user",
                    input.request().userId()
            );
            assertEquals(
                    "tool-selection-evaluation-" + testCase.id(),
                    input.request().sessionId()
            );
            assertEquals(
                    testCase.userMessage(),
                    input.request().question()
            );
            assertTrue(input.history().isEmpty());
            assertTrue(input.exchanges().isEmpty());
        }
    }

    @Test
    void shouldAggregateThreeToolSelectionErrorTypes() {
        RecordingScriptedModel model =
                new RecordingScriptedModel(
                        List.of(
                                toolTurn("get_product_detail"),
                                textTurn(),
                                toolTurn("list_my_orders"),
                                toolTurn("search_products")
                        )
                );

        ToolSelectionEvaluationRunner runner =
                new ToolSelectionEvaluationRunner(
                        model,
                        evaluation,
                        calculator
                );

        ToolSelectionEvaluationReport report =
                runner.run(fourActionCases());

        ToolSelectionMetrics metrics = report.metrics();

        assertEquals(4, metrics.total());
        assertEquals(1, metrics.correct());
        assertEquals(0.25, metrics.accuracy(), 0.000001);
        assertEquals(
                Map.of(
                        ErrorType.WRONG_TOOL, 1,
                        ErrorType.TOOL_UNDERUSE, 1,
                        ErrorType.TOOL_OVERUSE, 1
                ),
                metrics.errorCounts()
        );
    }

    @Test
    void shouldRejectMissingEvaluationCasesAndDependencies() {
        RecordingScriptedModel model =
                new RecordingScriptedModel(List.of(textTurn()));

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionEvaluationRunner(
                        null,
                        evaluation,
                        calculator
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionEvaluationRunner(
                        model,
                        null,
                        calculator
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionEvaluationRunner(
                        model,
                        evaluation,
                        null
                )
        );

        ToolSelectionEvaluationRunner runner =
                new ToolSelectionEvaluationRunner(
                        model,
                        evaluation,
                        calculator
                );

        assertThrows(
                NullPointerException.class,
                () -> runner.run(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> runner.run(List.of())
        );

        assertThrows(
                NullPointerException.class,
                () -> runner.run(
                        Arrays.asList(
                                fourActionCases().get(0),
                                null
                        )
                )
        );
    }

    @Test
    void shouldFailFastWhenModelCallFails() {
        RuntimeException modelFailure =
                new IllegalStateException("脚本模型故障");

        List<ModelInput> receivedInputs = new ArrayList<>();

        ChatModel failingModel = new ChatModel() {

            @Override
            public String chat(
                    ChatRequest request,
                    List<ChatMessage> history
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ModelTurn nextTurn(ModelInput input) {
                receivedInputs.add(input);

                if (receivedInputs.size() == 2) {
                    throw modelFailure;
                }

                return toolTurn("search_products");
            }
        };

        ToolSelectionEvaluationRunner runner =
                new ToolSelectionEvaluationRunner(
                        failingModel,
                        evaluation,
                        calculator
                );

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> runner.run(fourActionCases())
        );

        assertSame(modelFailure, actual);
        assertEquals(2, receivedInputs.size());
    }

    private List<ToolSelectionCase> fourActionCases() {
        return List.of(
                new ToolSelectionCase(
                        "search",
                        "搜索Java书",
                        Action.SEARCH_PRODUCTS,
                        "需要搜索商品"
                ),
                new ToolSelectionCase(
                        "detail",
                        "查看商品1详情",
                        Action.GET_PRODUCT_DETAIL,
                        "需要商品详情"
                ),
                new ToolSelectionCase(
                        "orders",
                        "查看我的订单",
                        Action.LIST_MY_ORDERS,
                        "需要订单列表"
                ),
                new ToolSelectionCase(
                        "none",
                        "你好",
                        Action.NONE,
                        "普通问候不需要工具"
                )
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

    private static final class RecordingScriptedModel
            implements ChatModel {

        private final List<ModelTurn> turns;
        private final List<ModelInput> inputs = new ArrayList<>();
        private int nextIndex;

        private RecordingScriptedModel(List<ModelTurn> turns) {
            this.turns = List.copyOf(turns);
        }

        @Override
        public String chat(
                ChatRequest request,
                List<ChatMessage> history
        ) {
            throw new UnsupportedOperationException(
                    "评测运行器应该调用 nextTurn"
            );
        }

        @Override
        public ModelTurn nextTurn(ModelInput input) {
            inputs.add(input);

            ModelTurn turn = turns.get(nextIndex);
            nextIndex++;
            return turn;
        }

        private List<ModelInput> inputs() {
            return List.copyOf(inputs);
        }

        private ModelTurn turnAt(int index) {
            return turns.get(index);
        }
    }
}
