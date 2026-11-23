package com.codesprint.lesson0831.agent.toolcalling;

import java.util.List;

public record ToolExchange(
        ModelTurn turn,
        List<ToolExecutionResult> results
) {
    public ToolExchange {
        if (turn == null || !turn.hasToolCalls()) {
            throw new IllegalArgumentException("必须包含工具调用");
        }

        if (results == null) {
            throw new IllegalArgumentException("工具结果列表不能为空");
        }

        results = List.copyOf(results);

        if (turn.toolCalls().size() != results.size()) {
            throw new IllegalArgumentException("工具调用与结果数量不一致");
        }

        for (int i = 0; i < results.size(); i++) {
            String callId = turn.toolCalls().get(i).id();
            ToolExecutionResult result = results.get(i);

            if (callId == null || callId.isBlank()) {
                throw new IllegalArgumentException("工具调用编号不能为空");
            }

            if (!callId.equals(result.toolCallId())) {
                throw new IllegalArgumentException("工具调用与结果编号不一致");
            }

            if (result.result() == null) {
                throw new IllegalArgumentException("工具执行结果不能为空");
            }
        }
    }
}
