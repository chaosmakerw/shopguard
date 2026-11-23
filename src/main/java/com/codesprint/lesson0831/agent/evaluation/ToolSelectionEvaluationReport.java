package com.codesprint.lesson0831.agent.evaluation;

import java.util.List;
import java.util.Objects;

public record ToolSelectionEvaluationReport(
        List<ToolSelectionCaseResult> caseResults,
        ToolSelectionMetrics metrics
) {
    public ToolSelectionEvaluationReport {
        Objects.requireNonNull(
                caseResults,
                "逐题评测结果不能为空"
        );

        caseResults = List.copyOf(
                caseResults
        );

        if (caseResults.isEmpty()) {
            throw new IllegalArgumentException(
                    "逐题评测结果不能为空"
            );
        }

        Objects.requireNonNull(
                metrics,
                "汇总指标不能为空"
        );

        if (caseResults.size()
                != metrics.total()) {
            throw new IllegalArgumentException(
                    "逐题结果数量必须与汇总样本数一致"
            );
        }
    }
}