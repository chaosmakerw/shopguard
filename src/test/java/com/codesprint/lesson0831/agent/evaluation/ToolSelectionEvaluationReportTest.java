package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.ErrorType;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Result;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolSelectionEvaluationReportTest {

    @Test
    void shouldKeepCaseEvidenceAndMetricsReadOnly() {
        ToolSelectionCase testCase = validCase();
        Result result = validResult();

        ToolSelectionEvaluationReport report =
                new ToolSelectionEvaluationReport(
                        List.of(
                                new ToolSelectionCaseResult(
                                        testCase,
                                        validTurn(),
                                        result
                                )
                        ),
                        validMetrics()
                );

        assertEquals(1, report.caseResults().size());
        assertEquals(testCase, report.caseResults().get(0).testCase());
        assertEquals(validTurn(), report.caseResults().get(0).modelTurn());
        assertEquals(result, report.caseResults().get(0).result());
        assertEquals(1, report.metrics().total());

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.caseResults().clear()
        );
    }

    @Test
    void shouldRejectMissingOrInconsistentReportData() {
        ToolSelectionCaseResult caseResult =
                new ToolSelectionCaseResult(
                        validCase(),
                        validTurn(),
                        validResult()
                );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionCaseResult(
                        null,
                        validTurn(),
                        validResult()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionCaseResult(
                        validCase(),
                        null,
                        validResult()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionCaseResult(
                        validCase(),
                        validTurn(),
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionCaseResult(
                        validCase(),
                        validTurn(),
                        new Result(
                                false,
                                Action.LIST_MY_ORDERS,
                                Action.NONE,
                                ErrorType.TOOL_UNDERUSE
                        )
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionEvaluationReport(
                        null,
                        validMetrics()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionEvaluationReport(
                        List.of(),
                        validMetrics()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionEvaluationReport(
                        Arrays.asList(caseResult, null),
                        validMetrics()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionEvaluationReport(
                        List.of(caseResult),
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionEvaluationReport(
                        List.of(caseResult, caseResult),
                        validMetrics()
                )
        );
    }

    private ToolSelectionCase validCase() {
        return new ToolSelectionCase(
                "search-java",
                "搜索Java书",
                Action.SEARCH_PRODUCTS,
                "需要搜索商品"
        );
    }

    private Result validResult() {
        return new Result(
                true,
                Action.SEARCH_PRODUCTS,
                Action.SEARCH_PRODUCTS,
                ErrorType.NONE
        );
    }

    private ModelTurn validTurn() {
        return new ModelTurn(
                null,
                List.of(
                        new ToolCall(
                                "call-1",
                                "search_products",
                                Map.of()
                        )
                )
        );
    }

    private ToolSelectionMetrics validMetrics() {
        return new ToolSelectionMetrics(
                1,
                1,
                1.0,
                Map.of(
                        Action.SEARCH_PRODUCTS,
                        new ToolSelectionMetrics.ActionMetrics(
                                1,
                                1,
                                1.0
                        )
                ),
                Map.of()
        );
    }
}
