package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ToolCallExecutorTest {

    private final ToolDispatcher dispatcher =
            mock(ToolDispatcher.class);

    private final ToolCallExecutor executor =
            new ToolCallExecutor(dispatcher);

    @Test
    void shouldPreserveSuccessfulResultAndCallId(){
        ToolCall call = new ToolCall(
                "call-001",
                "list_my_orders",
                Map.of()
        );
        ToolResult<String> expected =
                ToolResult.success("测试结果");
        doReturn(expected)
                .when(dispatcher)
                .dispatch(call.name(),call.arguments());
        ToolExecutionResult actual = executor.execute(call);
        assertEquals("call-001",actual.toolCallId());
        assertSame(expected,actual.result());
        verify(dispatcher)
                .dispatch("list_my_orders",Map.of());
    }
    @Test
    void shouldPreserveFailureResultAndCallId() {
        ToolCall call = new ToolCall(
                "call-002",
                "list_my_orders",
                Map.of()
        );

        ToolResult<Void> expected =
                ToolResult.failure("UNAUTHORIZED", "请先登录");

        doReturn(expected)
                .when(dispatcher)
                .dispatch(call.name(), call.arguments());

        ToolExecutionResult actual = executor.execute(call);

        assertEquals("call-002", actual.toolCallId());
        assertSame(expected, actual.result());
        assertFalse(actual.result().success());
        assertEquals("UNAUTHORIZED", actual.result().code());

        verify(dispatcher)
                .dispatch("list_my_orders", Map.of());
    }
    @Test
    void shouldRejectInvalidCallsWithoutCallingDispatcher() {
        assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(null)
        );

        String[] invalidIds = {null, "", "   "};

        for (String id : invalidIds) {
            ToolCall call = new ToolCall(
                    id,
                    "list_my_orders",
                    Map.of()
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> executor.execute(call)
            );
        }

        verifyNoInteractions(dispatcher);
    }
    @Test
    void shouldCollectResultsForAllToolCalls() {
        ToolCall first = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ToolCall second = new ToolCall(
                "call-002", "get_product_detail",
                Map.of("productId", 1L)
        );

        ToolResult<String> firstResult =
                ToolResult.failure("UNAUTHORIZED", "请先登录");
        ToolResult<String> secondResult =
                ToolResult.success("商品详情");

        doReturn(firstResult).when(dispatcher)
                .dispatch(first.name(), first.arguments());
        doReturn(secondResult).when(dispatcher)
                .dispatch(second.name(), second.arguments());

        ModelTurn turn = new ModelTurn(
                null, List.of(first, second)
        );

        List<ToolExecutionResult> actual =
                executor.executeAll(turn);

        assertEquals(
                List.of(
                        new ToolExecutionResult("call-001", firstResult),
                        new ToolExecutionResult("call-002", secondResult)
                ),
                actual
        );

        verify(dispatcher)
                .dispatch(first.name(), first.arguments());
        verify(dispatcher)
                .dispatch(second.name(), second.arguments());
    }
    @Test
    void shouldSkipExecutionForFinalAnswer() {
        ModelTurn turn = new ModelTurn(
                "你目前没有订单", List.of()
        );

        List<ToolExecutionResult> actual =
                executor.executeAll(turn);

        assertTrue(actual.isEmpty());
        verifyNoInteractions(dispatcher);
    }
    @Test
    void shouldRejectDuplicateIdsBeforeExecutingAnyTool() {
        ToolCall first = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ToolCall second = new ToolCall(
                "call-001", "get_product_detail",
                Map.of("productId", 1L)
        );

        ModelTurn turn = new ModelTurn(
                null, List.of(first, second)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeAll(turn)
        );

        verifyNoInteractions(dispatcher);
    }
    @Test
    void shouldRejectLaterBlankIdBeforeExecutingAnyTool() {
        ToolCall first = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ToolCall second = new ToolCall(
                " ", "get_product_detail",
                Map.of("productId", 1L)
        );

        ModelTurn turn = new ModelTurn(
                null, List.of(first, second)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeAll(turn)
        );

        verifyNoInteractions(dispatcher);
    }
}