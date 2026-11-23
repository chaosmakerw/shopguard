package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.ErrorType;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Result;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionMetrics.ActionMetrics;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolSelectionReportCalculatorTest {

    private final ToolSelectionEvaluation evaluation =
            new ToolSelectionEvaluation();

    private final ToolSelectionReportCalculator calculator =
            new ToolSelectionReportCalculator();

    @Test
    void shouldCalculateOverallAccuracyAndErrorCounts() {
        List<Result> results = List.of(
                toolResult(
                        Action.SEARCH_PRODUCTS,
                        "search_products"
                ),
                toolResult(
                        Action.GET_PRODUCT_DETAIL,
                        "get_product_detail"
                ),
                noToolResult(
                        Action.LIST_MY_ORDERS
                ),
                toolResult(
                        Action.NONE,
                        "search_products"
                ),
                toolResult(
                        Action.SEARCH_PRODUCTS,
                        "get_product_detail"
                )
        );

        ToolSelectionMetrics metrics =
                calculator.calculate(results);

        assertEquals(5, metrics.total());
        assertEquals(2, metrics.correct());
        assertEquals(0.4, metrics.accuracy(), 0.000001);

        assertEquals(
                Map.of(
                        ErrorType.TOOL_OVERUSE, 1,
                        ErrorType.TOOL_UNDERUSE, 1,
                        ErrorType.WRONG_TOOL, 1
                ),
                metrics.errorCounts()
        );
    }

    @Test
    void shouldCalculateAccuracyForEveryExpectedAction() {
        List<Result> results = List.of(
                toolResult(
                        Action.SEARCH_PRODUCTS,
                        "search_products"
                ),
                toolResult(
                        Action.SEARCH_PRODUCTS,
                        "get_product_detail"
                ),
                toolResult(
                        Action.GET_PRODUCT_DETAIL,
                        "get_product_detail"
                ),
                noToolResult(
                        Action.LIST_MY_ORDERS
                ),
                toolResult(
                        Action.NONE,
                        "search_products"
                )
        );

        ToolSelectionMetrics metrics =
                calculator.calculate(results);

        assertActionMetrics(
                metrics,
                Action.SEARCH_PRODUCTS,
                2,
                1,
                0.5
        );

        assertActionMetrics(
                metrics,
                Action.GET_PRODUCT_DETAIL,
                1,
                1,
                1.0
        );

        assertActionMetrics(
                metrics,
                Action.LIST_MY_ORDERS,
                1,
                0,
                0.0
        );

        assertActionMetrics(
                metrics,
                Action.NONE,
                1,
                0,
                0.0
        );
    }

    @Test
    void shouldIncludeZeroMetricsForActionsWithoutSamples() {
        ToolSelectionMetrics metrics =
                calculator.calculate(
                        List.of(
                                toolResult(
                                        Action.SEARCH_PRODUCTS,
                                        "search_products"
                                )
                        )
                );

        assertEquals(4, metrics.byAction().size());

        assertActionMetrics(
                metrics,
                Action.SEARCH_PRODUCTS,
                1,
                1,
                1.0
        );

        assertActionMetrics(
                metrics,
                Action.GET_PRODUCT_DETAIL,
                0,
                0,
                0.0
        );

        assertActionMetrics(
                metrics,
                Action.LIST_MY_ORDERS,
                0,
                0,
                0.0
        );

        assertActionMetrics(
                metrics,
                Action.NONE,
                0,
                0,
                0.0
        );
    }

    @Test
    void shouldRejectMissingOrEmptyEvaluationResults() {
        assertThrows(
                NullPointerException.class,
                () -> calculator.calculate(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(List.of())
        );

        assertThrows(
                NullPointerException.class,
                () -> calculator.calculate(
                        Arrays.asList(
                                toolResult(
                                        Action.SEARCH_PRODUCTS,
                                        "search_products"
                                ),
                                null
                        )
                )
        );
    }

    @Test
    void shouldExposeReadOnlyMetricMaps() {
        ToolSelectionMetrics metrics =
                calculator.calculate(
                        List.of(
                                toolResult(
                                        Action.SEARCH_PRODUCTS,
                                        "search_products"
                                )
                        )
                );

        assertThrows(
                UnsupportedOperationException.class,
                () -> metrics.byAction().put(
                        Action.NONE,
                        new ActionMetrics(
                                1,
                                1,
                                1.0
                        )
                )
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> metrics.errorCounts().put(
                        ErrorType.WRONG_TOOL,
                        99
                )
        );
    }

    private Result toolResult(
            Action expected,
            String actualToolName
    ) {
        return evaluation.evaluate(
                expected,
                new ModelTurn(
                        null,
                        List.of(
                                new ToolCall(
                                        "call-1",
                                        actualToolName,
                                        Map.of()
                                )
                        )
                )
        );
    }

    private Result noToolResult(
            Action expected
    ) {
        return evaluation.evaluate(
                expected,
                new ModelTurn(
                        "模型直接回答",
                        List.of()
                )
        );
    }

    private void assertActionMetrics(
            ToolSelectionMetrics metrics,
            Action action,
            int expectedTotal,
            int expectedCorrect,
            double expectedAccuracy
    ) {
        ActionMetrics actionMetrics =
                metrics.byAction().get(action);

        assertEquals(
                expectedTotal,
                actionMetrics.total()
        );

        assertEquals(
                expectedCorrect,
                actionMetrics.correct()
        );

        assertEquals(
                expectedAccuracy,
                actionMetrics.accuracy(),
                0.000001
        );
    }
}
