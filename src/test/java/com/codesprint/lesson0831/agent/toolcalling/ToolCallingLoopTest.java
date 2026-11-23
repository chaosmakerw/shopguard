package com.codesprint.lesson0831.agent.toolcalling;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.ModelCallException;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallingLoopTest {
    @Test
    void shouldSendToolResultBackToModel() {
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 1
        );

        ModelInput first = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn toolTurn = new ModelTurn(null, List.of(call));
        ToolResult<?> toolResult = ToolResult.success(List.of());

        ToolExchange exchange = new ToolExchange(
                toolTurn,
                List.of(new ToolExecutionResult("call-001", toolResult))
        );
        ModelInput second = first.withExchange(exchange);

        when(model.nextTurn(eq(first), any(TimeBudget.class))).thenReturn(toolTurn);
        doReturn(toolResult).when(dispatcher)
                .dispatch("list_my_orders", Map.of());
        when(model.nextTurn(eq(second), any(TimeBudget.class)))
                .thenReturn(new ModelTurn("你目前没有订单", List.of()));

        String answer = loop.run(first);

        assertEquals("你目前没有订单", answer);
        verify(model).nextTurn(eq(first), any(TimeBudget.class));
        verify(model).nextTurn(eq(second), any(TimeBudget.class));
        verifyNoMoreInteractions(model);
        verify(dispatcher).dispatch("list_my_orders", Map.of());
    }
    @Test
    void shouldRejectToolCallWhenBudgetIsZero(){
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 0
        );

        ModelInput input= new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn toolTurn = new ModelTurn(null, List.of(call));
        when(model.nextTurn(eq(input), any(TimeBudget.class))).thenReturn(toolTurn);

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> loop.run(input)
        );

        assertEquals("已达到最大工具轮次", exception.getMessage());

        verify(model).nextTurn(eq(input), any(TimeBudget.class));
        verifyNoMoreInteractions(model);
        verifyNoInteractions(dispatcher);
    }
    @Test
    void shouldStopBeforeExecutingSecondToolRound() {
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 1
        );

        ModelInput first = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        ToolCall firstCall = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn firstTurn = new ModelTurn(null, List.of(firstCall));
        ToolResult<?> result = ToolResult.success(List.of());

        ModelInput second = first.withExchange(
                new ToolExchange(
                        firstTurn,
                        List.of(new ToolExecutionResult("call-001", result))
                )
        );

        ToolCall secondCall = new ToolCall(
                "call-002", "list_my_orders", Map.of()
        );
        ModelTurn secondTurn = new ModelTurn(null, List.of(secondCall));

        when(model.nextTurn(eq(first), any(TimeBudget.class))).thenReturn(firstTurn);
        doReturn(result).when(dispatcher)
                .dispatch("list_my_orders", Map.of());
        when(model.nextTurn(eq(second), any(TimeBudget.class))).thenReturn(secondTurn);

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> loop.run(first)
        );

        assertEquals("已达到最大工具轮次", exception.getMessage());

        verify(model).nextTurn(eq(first), any(TimeBudget.class));
        verify(model).nextTurn(eq(second), any(TimeBudget.class));
        verifyNoMoreInteractions(model);

        verify(dispatcher, times(1))
                .dispatch("list_my_orders", Map.of());
        verifyNoMoreInteractions(dispatcher);
    }
    @Test
    void shouldRejectLateToolResponseBeforeExecutingTools() {
        AtomicLong now = new AtomicLong(100L);

        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 1
        );

        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        ModelTurn toolTurn = new ModelTurn(
                null,
                List.of(new ToolCall(
                        "call-001", "list_my_orders", Map.of()
                ))
        );

        when(model.nextTurn(eq(input), any(TimeBudget.class))).thenAnswer(invocation -> {
            now.set(110L);
            return toolTurn;
        });

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> loop.run(
                        input,
                        Duration.ofNanos(10),
                        () -> now.get()
                )
        );

        assertEquals("工具调用流程已超时", exception.getMessage());
        verify(model).nextTurn(eq(input), any(TimeBudget.class));
        verifyNoMoreInteractions(model);
        verifyNoInteractions(dispatcher);
    }
    @Test
    void shouldShareBudgetAcrossRoundsAndRejectLateAnswer() {
        AtomicLong now = new AtomicLong(100L);

        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 1
        );

        ModelInput first = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );
        ModelTurn toolTurn = new ModelTurn(null, List.of(call));
        ToolResult<?> result = ToolResult.success(List.of());

        ModelInput second = first.withExchange(
                new ToolExchange(
                        toolTurn,
                        List.of(new ToolExecutionResult("call-001", result))
                )
        );

        when(model.nextTurn(eq(first), any(TimeBudget.class))).thenAnswer(invocation -> {
            now.set(104L);
            return toolTurn;
        });

        when(dispatcher.dispatch("list_my_orders", Map.of()))
                .thenAnswer(invocation -> {
                    now.set(107L);
                    return result;
                });

        when(model.nextTurn(eq(second), any(TimeBudget.class))).thenAnswer(invocation -> {
            now.set(110L);
            return new ModelTurn("你目前没有订单", List.of());
        });

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> loop.run(
                        first, Duration.ofNanos(10), () -> now.get()
                )
        );

        assertEquals("工具调用流程已超时", exception.getMessage());

        verify(model).nextTurn(eq(first), any(TimeBudget.class));
        verify(model).nextTurn(eq(second), any(TimeBudget.class));
        verifyNoMoreInteractions(model);

        verify(dispatcher).dispatch("list_my_orders", Map.of());
        verifyNoMoreInteractions(dispatcher);
    }
    @Test
    void shouldStopAfterToolExhaustsBudget() {
        AtomicLong now = new AtomicLong(100L);

        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 1
        );

        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of()
        );

        ModelTurn toolTurn = new ModelTurn(
                null,
                List.of(new ToolCall(
                        "call-001", "list_my_orders", Map.of()
                ))
        );

        when(model.nextTurn(eq(input), any(TimeBudget.class))).thenAnswer(invocation -> {
            now.set(104L);
            return toolTurn;
        });

        when(dispatcher.dispatch("list_my_orders", Map.of()))
                .thenAnswer(invocation -> {
                    now.set(110L);
                    return ToolResult.success(List.of());
                });

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> loop.run(
                        input, Duration.ofNanos(10), () -> now.get()
                )
        );

        assertEquals("工具调用流程已超时", exception.getMessage());

        verify(model).nextTurn(eq(input), any(TimeBudget.class));
        verifyNoMoreInteractions(model);

        verify(dispatcher).dispatch("list_my_orders", Map.of());
        verifyNoMoreInteractions(dispatcher);
    }
    @Test
    void shouldStopBeforeSecondToolWhenFirstExhaustsBudget() {
        AtomicLong now = new AtomicLong(100L);

        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallingLoop loop = new ToolCallingLoop(
                model, new ToolCallExecutor(dispatcher), 1
        );

        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "查询订单和商品"),
                List.of(),
                List.of()
        );

        ModelTurn turn = new ModelTurn(
                null,
                List.of(
                        new ToolCall(
                                "call-001", "list_my_orders", Map.of()
                        ),
                        new ToolCall(
                                "call-002", "get_product_detail",
                                Map.of("productId", 1L)
                        )
                )
        );

        when(model.nextTurn(eq(input), any(TimeBudget.class))).thenReturn(turn);

        when(dispatcher.dispatch("list_my_orders", Map.of()))
                .thenAnswer(invocation -> {
                    now.set(110L);
                    return ToolResult.success(List.of());
                });

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> loop.run(
                        input, Duration.ofNanos(10), () -> now.get()
                )
        );

        assertEquals("工具调用流程已超时", exception.getMessage());

        verify(dispatcher).dispatch("list_my_orders", Map.of());
        verifyNoMoreInteractions(dispatcher);

        verify(model).nextTurn(eq(input), any(TimeBudget.class));
        verifyNoMoreInteractions(model);
    }
    @Test
    void run_should_execute_tool_and_return_final_answer() {
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallExecutor executor =
                new ToolCallExecutor(dispatcher);

        ToolCallingLoop loop =
                new ToolCallingLoop(
                        model,
                        executor,
                        3
                );

        ChatRequest request = new ChatRequest(
                "user-a",
                "session-1",
                "查询我的订单"
        );

        ModelInput initialInput = new ModelInput(
                request,
                List.of(),
                List.of()
        );


        // 第一次模型调用：模型要求执行工具
        ToolCall toolCall = new ToolCall(
                "call-001",
                "list_my_orders",
                Map.of()
        );

        ModelTurn toolTurn = new ModelTurn(
                null,
                List.of(toolCall)
        );


        // Java 工具执行结果
        ToolResult<List<OrderSummary>> toolResult =
                ToolResult.success(
                        List.of(
                                new OrderSummary(
                                        1L,
                                        1001L,
                                        "Java编程思想",
                                        "PAID"
                                )
                        )
                );


        // 第二次模型调用：模型拿到工具结果后给最终答案
        ModelTurn finalTurn = new ModelTurn(
                "你目前有一笔订单：Java编程思想。",
                List.of()
        );

        when(model.nextTurn(any(ModelInput.class), any(TimeBudget.class)))
                .thenReturn(toolTurn)
                .thenReturn(finalTurn);

        doReturn(toolResult)
                .when(dispatcher)
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );


        // 真正运行完整工具循环
        String answer = loop.run(initialInput);


        // 最终自然语言答案
        assertEquals(
                "你目前有一笔订单：Java编程思想。",
                answer
        );


        // 工具确实执行过一次
        verify(dispatcher, times(1))
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );


        // 模型应该被调用两次：
        // 第一次拿 tool call
        // 第二次拿最终答案
        ArgumentCaptor<ModelInput> inputCaptor =
                ArgumentCaptor.forClass(ModelInput.class);

        verify(model, times(2))
                .nextTurn(inputCaptor.capture(), any(TimeBudget.class));

        List<ModelInput> modelInputs =
                inputCaptor.getAllValues();


        // 第一次调用模型时，还没有任何工具交互
        ModelInput firstInput = modelInputs.get(0);

        assertTrue(
                firstInput.exchanges().isEmpty()
        );


        // 第二次调用模型时，
        // ToolCallingLoop 应该自动生成了 ToolExchange
        ModelInput secondInput = modelInputs.get(1);

        assertEquals(
                1,
                secondInput.exchanges().size()
        );

        ToolExchange exchange =
                secondInput.exchanges().get(0);


        // exchange 中应该保存第一次模型的 tool call
        assertEquals(
                toolTurn,
                exchange.turn()
        );

        assertEquals(
                1,
                exchange.results().size()
        );


        // 工具执行结果必须和原 tool call 使用同一个 id
        ToolExecutionResult executionResult =
                exchange.results().get(0);

        assertEquals(
                "call-001",
                executionResult.toolCallId()
        );

        assertEquals(
                toolResult,
                executionResult.result()
        );
    }
    @Test
    void run_should_stop_when_max_tool_rounds_reached() {
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallExecutor executor =
                new ToolCallExecutor(dispatcher);

        // 最多只允许执行 1 轮工具
        ToolCallingLoop loop =
                new ToolCallingLoop(
                        model,
                        executor,
                        1
                );

        ChatRequest request = new ChatRequest(
                "user-a",
                "session-1",
                "查询我的订单"
        );

        ModelInput initialInput = new ModelInput(
                request,
                List.of(),
                List.of()
        );

        // 第一次模型调用：要求执行第一个工具
        ModelTurn firstTurn = new ModelTurn(
                null,
                List.of(
                        new ToolCall(
                                "call-001",
                                "list_my_orders",
                                Map.of()
                        )
                )
        );

        // 第二次模型调用：又要求执行工具
        ModelTurn secondTurn = new ModelTurn(
                null,
                List.of(
                        new ToolCall(
                                "call-002",
                                "list_my_orders",
                                Map.of()
                        )
                )
        );

        when(model.nextTurn(any(ModelInput.class), any(TimeBudget.class)))
                .thenReturn(firstTurn)
                .thenReturn(secondTurn);

        ToolResult<List<OrderSummary>> toolResult =
                ToolResult.success(
                        List.of(
                                new OrderSummary(
                                        1L,
                                        1001L,
                                        "Java编程思想",
                                        "PAID"
                                )
                        )
                );

        doReturn(toolResult)
                .when(dispatcher)
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );

        ModelCallException exception =
                assertThrows(
                        ModelCallException.class,
                        () -> loop.run(initialInput)
                );

        assertEquals(
                "已达到最大工具轮次",
                exception.getMessage()
        );

        // 第一次工具允许执行
        verify(dispatcher, times(1))
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );

        // 模型会被调用两次：
        // 第一次产生 call-001
        // 第二次产生 call-002，然后被 maxToolRounds 拦住
        verify(model, times(2))
                .nextTurn(any(ModelInput.class), any(TimeBudget.class));
    }
    @Test
    void run_should_stop_when_time_budget_expires_after_model_call() {
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallExecutor executor =
                new ToolCallExecutor(dispatcher);

        ToolCallingLoop loop =
                new ToolCallingLoop(
                        model,
                        executor,
                        3
                );

        ChatRequest request = new ChatRequest(
                "user-a",
                "session-1",
                "查询我的订单"
        );

        ModelInput initialInput = new ModelInput(
                request,
                List.of(),
                List.of()
        );

        ModelTurn toolTurn = new ModelTurn(
                null,
                List.of(
                        new ToolCall(
                                "call-001",
                                "list_my_orders",
                                Map.of()
                        )
                )
        );

        AtomicLong now = new AtomicLong(0L);

        when(model.nextTurn(any(ModelInput.class), any(TimeBudget.class)))
                .thenAnswer(invocation -> {
                    // 模拟这次模型调用耗时 2 秒
                    now.set(Duration.ofSeconds(2).toNanos());

                    return toolTurn;
                });

        ModelCallException exception =
                assertThrows(
                        ModelCallException.class,
                        () -> loop.run(
                                initialInput,
                                Duration.ofSeconds(1),
                                now::get
                        )
                );

        assertEquals(
                "工具调用流程已超时",
                exception.getMessage()
        );

        // 模型虽然已经返回了 ToolCall，
        // 但预算已经过期，所以工具绝不能执行。
        verifyNoInteractions(dispatcher);
    }
    @Test
    void run_should_stop_between_tool_calls_when_budget_expires() {
        ChatModel model = mock(ChatModel.class);
        ToolDispatcher dispatcher = mock(ToolDispatcher.class);

        ToolCallExecutor executor =
                new ToolCallExecutor(dispatcher);

        ToolCallingLoop loop =
                new ToolCallingLoop(
                        model,
                        executor,
                        3
                );

        ChatRequest request = new ChatRequest(
                "user-a",
                "session-1",
                "查询订单和商品"
        );

        ModelInput initialInput = new ModelInput(
                request,
                List.of(),
                List.of()
        );

        ToolCall firstCall = new ToolCall(
                "call-001",
                "list_my_orders",
                Map.of()
        );

        ToolCall secondCall = new ToolCall(
                "call-002",
                "get_product_detail",
                Map.of("productId", 1L)
        );

        ModelTurn toolTurn = new ModelTurn(
                null,
                List.of(
                        firstCall,
                        secondCall
                )
        );

        AtomicLong now = new AtomicLong(0L);

        when(model.nextTurn(any(ModelInput.class), any(TimeBudget.class)))
                .thenReturn(toolTurn);

        doAnswer(invocation -> {
            // 第一个工具执行过程中，
            // 总耗时推进到了 2 秒。
            now.set(Duration.ofSeconds(2).toNanos());

            return ToolResult.success(List.of());
        })
                .when(dispatcher)
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );

        ModelCallException exception =
                assertThrows(
                        ModelCallException.class,
                        () -> loop.run(
                                initialInput,
                                Duration.ofSeconds(1),
                                now::get
                        )
                );

        assertEquals(
                "工具调用流程已超时",
                exception.getMessage()
        );

        // 第一个工具已经执行。
        verify(dispatcher, times(1))
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );

        // 预算在第一个工具之后已经耗尽，
        // 所以第二个工具绝不能执行。
        verify(dispatcher, never())
                .dispatch(
                        "get_product_detail",
                        Map.of("productId", 1L)
                );
    }
}
