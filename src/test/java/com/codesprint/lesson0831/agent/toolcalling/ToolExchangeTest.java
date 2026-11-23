package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
public class ToolExchangeTest {
    @Test
    void shouldKeepMatchingCallAndResult() {
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

        assertSame(turn, exchange.turn());
        assertEquals(List.of(result), exchange.results());

    }
    @Test
    void shouldRejectMissingResult(){
        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn turn = new ModelTurn(null, List.of(call));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolExchange(turn, List.of())
        );
    }
    @Test
    void shouldRejectMismatchedCallId(){
        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn turn = new ModelTurn(null, List.of(call));
        ToolExecutionResult wrongResult = new ToolExecutionResult(
                "call-999", ToolResult.success(List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolExchange(turn,List.of(wrongResult))
        );
    }
}

