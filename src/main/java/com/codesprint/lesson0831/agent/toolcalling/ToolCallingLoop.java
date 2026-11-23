package com.codesprint.lesson0831.agent.toolcalling;
import java.time.Duration;
import java.util.function.LongSupplier;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ModelCallException;

import java.util.List;
import java.util.Objects;
import com.codesprint.lesson0831.observability.AgentTrace;
public class ToolCallingLoop {
    private final ChatModel model;
    private final ToolCallExecutor executor;
    private final int maxToolRounds;
    public ToolCallingLoop(
            ChatModel model,
            ToolCallExecutor executor,
            int maxToolRounds
    ){
        if(maxToolRounds<0){
            throw new IllegalArgumentException("最大工具轮次不能为负数");
        }
        this.model = Objects.requireNonNull(model);
        this.executor = Objects.requireNonNull(executor);
        this.maxToolRounds = maxToolRounds;
    }
    public String run(ModelInput initialInput) {
        return run(
                initialInput,
                Duration.ofSeconds(10),
                () -> System.nanoTime()
        );
    }

    public String run(
            ModelInput initialInput,
            Duration timeout,
            LongSupplier nanoTime
    ) {
        if (initialInput == null) {
            throw new IllegalArgumentException("模型输入不能为空");
        }

        TimeBudget budget = new TimeBudget(timeout, nanoTime);
        ModelInput current = initialInput;
        int executedRounds = 0;

        while (true) {
            budget.checkNotExpired();

            AgentTrace.modelStarted();
            ModelTurn turn;
            try (var step = AgentTrace.begin("model", "模型调用 · 第 " + (executedRounds + 1) + " 轮")) {
                turn = model.nextTurn(current, budget);
                if (turn != null) step.success(turn.hasToolCalls() ? "返回工具调用，继续查询业务数据" : "回答生成完成");
            }

            budget.checkNotExpired();

            if (turn == null) {
                throw new ModelCallException("模型返回了空响应");
            }

            if (!turn.hasToolCalls()) {
                return turn.text();
            }

            if (executedRounds >= maxToolRounds) {
                throw new ModelCallException("已达到最大工具轮次");
            }

            budget.checkNotExpired();

            AgentTrace.tools(turn.toolCalls().stream().map(ToolCall::name).toList());
            List<ToolExecutionResult> results;
            try (var step = AgentTrace.begin("tool", String.join(" · ", turn.toolCalls().stream().map(ToolCall::name).toList()))) {
                results = executor.executeAll(turn, () -> budget.checkNotExpired());
                long completed = results.stream().filter(result -> result.result() != null && result.result().success()).count();
                if (completed == results.size()) step.success("已通过工具入口执行，完成 " + completed + " 项查询");
                else step.rejected("工具返回业务拒绝；成功 " + completed + " / " + results.size() + " 项");
            }

            budget.checkNotExpired();

            ToolExchange exchange = new ToolExchange(turn, results);
            current = current.withExchange(exchange);
            executedRounds++;
        }
    }

}
