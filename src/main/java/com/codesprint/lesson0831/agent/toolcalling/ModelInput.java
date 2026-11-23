package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatRequest;

import java.util.ArrayList;
import java.util.List;

public record ModelInput(
        ChatRequest request,
        List<ChatMessage> history,
        List<ToolExchange> exchanges
) {
    public ModelInput {
        if (request == null || history == null || exchanges == null) {
            throw new IllegalArgumentException("模型输入不能缺少必要结构");
        }

        history = List.copyOf(history);
        exchanges = List.copyOf(exchanges);
    }

    public ModelInput withExchange(ToolExchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("工具交互不能为空");
        }

        List<ToolExchange> updated = new ArrayList<>(exchanges);
        updated.add(exchange);

        return new ModelInput(request, history, updated);
    }
}