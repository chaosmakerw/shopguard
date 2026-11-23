package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.ErrorType;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Result;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionMetrics.ActionMetrics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ToolSelectionReportCalculator {

    private static final List<Action> EXPECTED_ACTIONS =
            List.of(
                    Action.SEARCH_PRODUCTS,
                    Action.GET_PRODUCT_DETAIL,
                    Action.LIST_MY_ORDERS,
                    Action.NONE
            );

    public ToolSelectionMetrics calculate(
            List<Result> results
    ) {
        Objects.requireNonNull(
                results,
                "评测结果列表不能为空"
        );

        if (results.isEmpty()) {
            throw new IllegalArgumentException(
                    "评测结果列表不能为空"
            );
        }

        EnumMap<Action, MutableCounter> counters =
                new EnumMap<>(Action.class);

        for (Action action : EXPECTED_ACTIONS) {
            counters.put(
                    action,
                    new MutableCounter()
            );
        }

        EnumMap<ErrorType, Integer> errorCounts =
                new EnumMap<>(ErrorType.class);

        int correct = 0;

        for (Result result : results) {
            Objects.requireNonNull(
                    result,
                    "评测结果不能包含null"
            );

            MutableCounter counter =
                    counters.get(result.expected());

            if (counter == null) {
                throw new IllegalArgumentException(
                        "结果中包含非法预期动作: "
                                + result.expected()
                );
            }

            counter.total++;

            if (result.correct()) {
                correct++;
                counter.correct++;
            } else {
                errorCounts.merge(
                        result.errorType(),
                        1,
                        Integer::sum
                );
            }
        }

        EnumMap<Action, ActionMetrics> byAction =
                new EnumMap<>(Action.class);

        for (Map.Entry<Action, MutableCounter> entry
                : counters.entrySet()) {
            MutableCounter counter =
                    entry.getValue();

            byAction.put(
                    entry.getKey(),
                    new ActionMetrics(
                            counter.total,
                            counter.correct,
                            calculateAccuracy(
                                    counter.correct,
                                    counter.total
                            )
                    )
            );
        }

        return new ToolSelectionMetrics(
                results.size(),
                correct,
                calculateAccuracy(
                        correct,
                        results.size()
                ),
                byAction,
                errorCounts
        );
    }

    private double calculateAccuracy(
            int correct,
            int total
    ) {
        if (total == 0) {
            return 0.0;
        }

        return (double) correct / total;
    }

    private static final class MutableCounter {

        private int total;
        private int correct;
    }
}