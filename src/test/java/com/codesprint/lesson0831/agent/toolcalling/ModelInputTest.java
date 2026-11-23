package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelInputTest {
    @Test
    void shouldAppendExchangeWithoutChangingPreviousInput() {
        ChatRequest request = new ChatRequest(
                "user-a", "session-1", "我的订单有哪些"
        );
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "你好", 1L),
                new ChatMessage("assistant", "你好，有什么可以帮你", 2L)
        );

        ModelInput first = new ModelInput(
                request, history, List.of()
        );

        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn turn = new ModelTurn(null, List.of(call));
        ToolExecutionResult result = new ToolExecutionResult(
                "call-001", ToolResult.success(List.of())
        );
        ToolExchange exchange = new ToolExchange(
                turn, List.of(result)
        );

        ModelInput second = first.withExchange(exchange);

        assertTrue(first.exchanges().isEmpty());
        assertEquals(List.of(exchange), second.exchanges());
        assertSame(request, second.request());
        assertEquals(history, second.history());
        assertNotSame(first, second);
    }
    @Test
    void shouldRejectNullExchange() {
        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> input.withExchange(null)
        );

        assertTrue(input.exchanges().isEmpty());
    }
}