package com.codesprint.lesson0831.agent;
import com.codesprint.lesson0831.agent.toolcalling.*;
import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
class ChatModelNextTurnTest {
    @Test
    void shouldWrapTextAnswerAsModelTurn() {
        ChatModel model = (request, history) ->
                "测试回答：" + request.question();

        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "你好"),
                List.of(),
                List.of()
        );

        ModelTurn turn = model.nextTurn(input);

        assertEquals("测试回答：你好", turn.text());
        assertFalse(turn.hasToolCalls());
    }
    @Test
    void shouldRejectToolExchangesBeforeCallingTextChat() {
        ChatModel model = (request, history) -> {
            throw new AssertionError("不应该调用旧的文本聊天方法");
        };

        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ToolExchange exchange = new ToolExchange(
                new ModelTurn(null, List.of(call)),
                List.of(new ToolExecutionResult(
                        "call-001",
                        ToolResult.success(List.of())
                ))
        );

        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of(exchange)
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> model.nextTurn(input)
        );
    }
}
