package com.codesprint.lesson0831.agent.toolcalling;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelTurnTest {
    @Test
    void shouldRepresentFinalAnswer(){
        ModelTurn turn =new ModelTurn(
                "你目前没有订单",
                List.of()
        );
        assertEquals("你目前没有订单",turn.text());
        assertFalse(turn.hasToolCalls());
        assertTrue(turn.toolCalls().isEmpty());
    }
    @Test
    void shouldRepresentToolCallsWithText(){
        ToolCall call = new ToolCall(
                "call-001",
                "list_my_orders",
                Map.of()
        );
        ModelTurn turn =new ModelTurn(
                "我先查询你的订单",
                List.of(call)
        );
        assertTrue(turn.hasToolCalls());
        assertEquals("我先查询你的订单",turn.text());
        assertEquals(List.of(call),turn.toolCalls());
    }
    @Test
    void shouldRejectEmptyResponse(){
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelTurn(null, List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelTurn("", List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelTurn("   ", List.of())
        );
    }
}
