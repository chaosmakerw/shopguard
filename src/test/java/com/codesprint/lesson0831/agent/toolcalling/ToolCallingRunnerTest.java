package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ToolCallingRunnerTest {

    @Test
    void shouldReturnTextWhenModelDoesNotRequestTool() {
        ChatModel model = (request, history) ->
                "回答：" + request.question();

        ToolDispatcher dispatcher =
                mock(ToolDispatcher.class);

        ToolCallingRunner runner = new ToolCallingRunner(
                model,
                new ToolCallExecutor(dispatcher),
                3,
                Duration.ofSeconds(10)
        );

        String answer = runner.run(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "你好"
                ),
                List.of()
        );

        assertEquals("回答：你好", answer);
    }

    @Test
    void shouldExecuteToolAndReturnFinalAnswer() {
        ChatModel model = new ChatModel() {

            @Override
            public String chat(
                    ChatRequest request,
                    List<ChatMessage> history
            ) {
                throw new AssertionError(
                        "工具循环不应该调用旧的 chat 方法"
                );
            }

            @Override
            public ModelTurn nextTurn(ModelInput input) {
                if (input.exchanges().isEmpty()) {
                    ToolCall call = new ToolCall(
                            "call-001",
                            "list_my_orders",
                            Map.of()
                    );

                    return new ModelTurn(
                            null,
                            List.of(call)
                    );
                }

                return new ModelTurn(
                        "你目前没有订单",
                        List.of()
                );
            }
        };

        ToolDispatcher dispatcher =
                mock(ToolDispatcher.class);

        doReturn(ToolResult.success(List.of()))
                .when(dispatcher)
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );

        ToolCallingRunner runner = new ToolCallingRunner(
                model,
                new ToolCallExecutor(dispatcher),
                3,
                Duration.ofSeconds(10)
        );

        String answer = runner.run(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "我的订单有哪些"
                ),
                List.of()
        );

        assertEquals("你目前没有订单", answer);

        verify(
                dispatcher,
                times(1)
        ).dispatch(
                "list_my_orders",
                Map.of()
        );
    }
}
