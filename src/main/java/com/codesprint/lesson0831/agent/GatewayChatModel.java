package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.TimeBudget;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class GatewayChatModel implements ChatModel {
    private final String model;
    private final GatewayHttpTransport transport;
    private final Duration defaultTimeout;
    private final GatewayRequestMapper requestMapper = new GatewayRequestMapper();
    private final GatewayResponseMapper responseMapper = new GatewayResponseMapper();

    public GatewayChatModel(
            String model, GatewayHttpTransport transport, Duration defaultTimeout
    ) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("网关逻辑模型不能为空");
        }
        this.model = model;
        this.transport = Objects.requireNonNull(transport);
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout);
        if (defaultTimeout.isZero() || defaultTimeout.isNegative()) {
            throw new IllegalArgumentException("默认超时时间必须大于零");
        }
    }

    @Override
    public String chat(ChatRequest request, List<ChatMessage> history) {
        TimeBudget budget = newBudget();
        ModelRequest modelRequest = requestMapper.toRequest(model, request, history);
        ModelResponse response = transport.send(modelRequest, budget);
        budget.checkNotExpired();
        ModelMessage message = response.choices().get(0).message();
        ModelTurn turn = responseMapper.toModelTurn(message);
        budget.checkNotExpired();
        if (turn.hasToolCalls()) {
            throw new ModelCallException("纯文本入口不接受工具调用");
        }
        return turn.text();
    }

    @Override
    public ModelTurn nextTurn(ModelInput input) {
        return nextTurn(input, newBudget());
    }

    @Override
    public ModelTurn nextTurn(ModelInput input, TimeBudget budget) {
        Objects.requireNonNull(budget, "调用预算不能为空");
        budget.checkNotExpired();
        try {
            ModelRequest request = requestMapper.toRequest(model, input);

           ModelResponse response = transport.send(request,budget);
           budget.checkNotExpired();
           ModelMessage message = response.choices().get(0).message();
            ModelTurn turn = responseMapper.toModelTurn(message);
            budget.checkNotExpired();
            return turn;
        } catch (JsonProcessingException invalidJson) {
            throw new ModelCallException("网关请求格式错误", invalidJson);
        }
    }

    private TimeBudget newBudget() {
        return new TimeBudget(defaultTimeout, () -> System.nanoTime());
    }
}

