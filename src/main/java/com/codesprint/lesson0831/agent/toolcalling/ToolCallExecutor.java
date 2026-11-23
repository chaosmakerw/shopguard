package com.codesprint.lesson0831.agent.toolcalling;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;


public class ToolCallExecutor {
    private final ToolDispatcher dispatcher;
    public ToolCallExecutor(ToolDispatcher dispatcher){
        this.dispatcher = dispatcher;
    }
    public ToolExecutionResult execute(ToolCall call){
        if(call == null){
            throw new IllegalArgumentException("工具调用不能为空");
        }
        if(call.id()==null ||call.id().isBlank()){
            throw new IllegalArgumentException("工具调用编号不能为空");
        }
        ToolResult<?> result = dispatcher.dispatch(call.name(),call.arguments());
        return new ToolExecutionResult(call.id(),result);
    }
    public List<ToolExecutionResult> executeAll(ModelTurn turn) {
        return executeAll(turn, () -> {});
    }

    public List<ToolExecutionResult> executeAll(
            ModelTurn turn,
            Runnable checkBudget
    ) {
        if (turn == null) {
            throw new IllegalArgumentException("模型响应不能为空");
        }
        if (checkBudget == null) {
            throw new IllegalArgumentException("预算检查动作不能为空");
        }

        checkBudget.run();

        Set<String> ids = new HashSet<>();

        for (ToolCall call : turn.toolCalls()) {
            String id = call.id();

            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("工具调用编号不能为空");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("工具调用编号不能重复");
            }
        }

        List<ToolExecutionResult> results = new ArrayList<>();

        for (ToolCall call : turn.toolCalls()) {
            checkBudget.run();

            ToolExecutionResult result = execute(call);

            checkBudget.run();
            results.add(result);
        }

        return List.copyOf(results);
    }
}
