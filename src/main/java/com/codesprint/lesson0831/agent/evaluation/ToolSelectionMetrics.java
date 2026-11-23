package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.ErrorType;

import java.util.Map;
import java.util.Objects;

public record ToolSelectionMetrics(
        int total,
        int correct,
        double accuracy,
        Map<Action, ActionMetrics> byAction,
        Map<ErrorType, Integer> errorCounts
) {
    public ToolSelectionMetrics {
        if (total <= 0) {
            throw new IllegalArgumentException(
                    "评测样本总数必须大于0"
            );
        }

        if (correct < 0 || correct > total) {
            throw new IllegalArgumentException(
                    "正确数量必须在0到总数之间"
            );
        }

        if (!Double.isFinite(accuracy)
                || accuracy < 0.0
                || accuracy > 1.0) {
            throw new IllegalArgumentException(
                    "正确率必须在0到1之间"
            );
        }

        byAction = Map.copyOf(
                Objects.requireNonNull(
                        byAction,
                        "分类指标不能为空"
                )
        );

        errorCounts = Map.copyOf(
                Objects.requireNonNull(
                        errorCounts,
                        "错误统计不能为空"
                )
        );
    }

    public record ActionMetrics(
            int total,
            int correct,
            double accuracy
    ) {
        public ActionMetrics {
            if (total < 0) {
                throw new IllegalArgumentException(
                        "分类样本数不能为负数"
                );
            }

            if (correct < 0 || correct > total) {
                throw new IllegalArgumentException(
                        "分类正确数必须在0到分类总数之间"
                );
            }

            if (!Double.isFinite(accuracy)
                    || accuracy < 0.0
                    || accuracy > 1.0) {
                throw new IllegalArgumentException(
                        "分类正确率必须在0到1之间"
                );
            }
        }
    }
}