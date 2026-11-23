package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.codesprint.lesson0831.agent.toolcalling.TimeBudget;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.junit.jupiter.api.Assertions.assertSame;
class AssistantServiceTest {

    private ToolCallingRunner createRunner(
            ChatModel model
    ) {
        ToolDispatcher dispatcher =
                mock(ToolDispatcher.class);

        ToolCallExecutor executor =
                new ToolCallExecutor(dispatcher);

        return new ToolCallingRunner(
                model,
                executor,
                3,
                Duration.ofSeconds(10)
        );
    }

    @Test
    void secondTurnShouldReceiveOnlyItsOwnPreviousMessages() {
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        ArrayList<List<ChatMessage>> received =
                new ArrayList<>();

        ChatModel model = (request, history) -> {
            received.add(history);
            return "回答：" + request.question();
        };

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        memory
                );

        service.ask(new ChatRequest(
                "a",
                "shared",
                "我叫小王"
        ));

        service.ask(new ChatRequest(
                "b",
                "shared",
                "B 的秘密"
        ));

        service.ask(new ChatRequest(
                "a",
                "other",
                "另一个会话"
        ));

        service.ask(new ChatRequest(
                "a",
                "shared",
                "我叫什么"
        ));

        assertTrue(received.get(0).isEmpty());
        assertTrue(received.get(1).isEmpty());
        assertTrue(received.get(2).isEmpty());

        assertEquals(
                List.of(
                        new ChatMessage(
                                "user",
                                "我叫小王",
                                1
                        ),
                        new ChatMessage(
                                "assistant",
                                "回答：我叫小王",
                                2
                        )
                ),
                received.get(3)
        );

        assertEquals(
                4,
                memory.load(
                        new ConversationKey(
                                "a",
                                "shared"
                        )
                ).size()
        );
    }

    @Test
    void windowShouldKeepSystemAndRecentHistory() {
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        ConversationKey key = new ConversationKey(
                "window-user",
                "window-session"
        );

        ChatMessage system = new ChatMessage(
                "system",
                "系统规则",
                0
        );

        memory.append(key, system);

        for (int i = 1; i <= 12; i++) {
            memory.append(
                    key,
                    new ChatMessage(
                            i % 2 == 1
                                    ? "user"
                                    : "assistant",
                            "消息" + i,
                            i
                    )
            );
        }

        ChatModel model = (request, history) -> {
            assertEquals(11, history.size());
            assertEquals(system, history.get(0));
            assertEquals(
                    "消息3",
                    history.get(1).content()
            );
            assertEquals(
                    "消息12",
                    history.get(10).content()
            );
            return "新回答";
        };

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        memory
                );

        service.ask(new ChatRequest(
                "window-user",
                "window-session",
                "新问题"
        ));

        assertEquals(
                15,
                memory.load(key).size()
        );
    }

    @Test
    void failedTurnShouldPreserveExistingHistory() {
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        ConversationKey key = new ConversationKey(
                "a",
                "failure"
        );

        memory.append(
                key,
                new ChatMessage(
                        "user",
                        "旧问题",
                        1
                )
        );

        memory.append(
                key,
                new ChatMessage(
                        "assistant",
                        "旧回答",
                        2
                )
        );

        List<ChatMessage> before =
                List.copyOf(memory.load(key));

        ChatModel model = (request, history) -> {
            assertEquals(before, history);
            throw new ModelCallException("模拟失败");
        };

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        memory
                );

        assertThrows(
                ModelCallException.class,
                () -> service.ask(
                        new ChatRequest(
                                "a",
                                "failure",
                                "新问题"
                        )
                )
        );

        assertEquals(
                before,
                memory.load(key)
        );
    }

    @Test
    void askShouldWrapFinalAnswer() {
        ChatModel model = (request, history) ->
                "测试回答：" + request.question();

        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        memory
                );

        ChatResponse response = service.ask(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "什么是 Bean？"
                )
        );

        assertEquals(
                "测试回答：什么是 Bean？",
                response.answer()
        );

        assertEquals(
                2,
                memory.load(
                        new ConversationKey(
                                "user-a",
                                "session-1"
                        )
                ).size()
        );
    }

    @Test
    void askShouldRejectBlankQuestion() {
        ChatModel model = (request, history) ->
                "不应该被调用";

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        new InMemoryConversationMemory()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ask(
                        new ChatRequest(
                                "user-a",
                                "session-1",
                                "  "
                        )
                )
        );
    }

    @Test
    void sameSessionShouldIsolateDifferentUsers() {
        ChatModel model = (request, history) ->
                "回答：" + request.question();

        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        memory
                );

        service.ask(new ChatRequest(
                "user-a",
                "session-1",
                "用户 A 的问题"
        ));

        service.ask(new ChatRequest(
                "user-b",
                "session-1",
                "用户 B 的问题"
        ));

        List<ChatMessage> userAMessages =
                memory.load(
                        new ConversationKey(
                                "user-a",
                                "session-1"
                        )
                );

        List<ChatMessage> userBMessages =
                memory.load(
                        new ConversationKey(
                                "user-b",
                                "session-1"
                        )
                );

        assertEquals(2, userAMessages.size());
        assertEquals(2, userBMessages.size());

        assertEquals(
                "用户 A 的问题",
                userAMessages.get(0).content()
        );

        assertEquals(
                "用户 B 的问题",
                userBMessages.get(0).content()
        );
    }

    @Test
    void askShouldRejectBlankUserId() {
        ChatModel model = (request, history) ->
                "不应该被调用";

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        new InMemoryConversationMemory()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ask(
                        new ChatRequest(
                                " ",
                                "session-1",
                                "你好"
                        )
                )
        );
    }

    @Test
    void askShouldRejectBlankSessionId() {
        ChatModel model = (request, history) ->
                "不应该被调用";

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        new InMemoryConversationMemory()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ask(
                        new ChatRequest(
                                "user-a",
                                " ",
                                "你好"
                        )
                )
        );
    }

    @Test
    void askShouldNotPersistWhenModelFails() {
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        ChatModel model = (request, history) -> {
            throw new ModelCallException(
                    "模拟模型失败"
            );
        };

        AssistantService service =
                new AssistantService(
                        createRunner(model),
                        memory
                );

        assertThrows(
                ModelCallException.class,
                () -> service.ask(
                        new ChatRequest(
                                "failure-user",
                                "failure-session",
                                "不会被保存的问题"
                        )
                )
        );

        assertTrue(
                memory.load(
                        new ConversationKey(
                                "failure-user",
                                "failure-session"
                        )
                ).isEmpty()
        );
    }

    @Test
    void askShouldExecuteToolAndPersistFinalAnswer() {
        ToolDispatcher dispatcher =
                mock(ToolDispatcher.class);

        doReturn(ToolResult.success(List.of()))
                .when(dispatcher)
                .dispatch(
                        "list_my_orders",
                        Map.of()
                );

        ChatModel model = new ChatModel() {

            @Override
            public String chat(
                    ChatRequest request,
                    List<ChatMessage> history
            ) {
                throw new AssertionError(
                        "不应该调用旧的 chat 方法"
                );
            }

            @Override
            public ModelTurn nextTurn(ModelInput input) {
                if (input.exchanges().isEmpty()) {
                    return new ModelTurn(
                            null,
                            List.of(
                                    new ToolCall(
                                            "call-001",
                                            "list_my_orders",
                                            Map.of()
                                    )
                            )
                    );
                }

                return new ModelTurn(
                        "你目前没有订单",
                        List.of()
                );
            }
        };

        ToolCallingRunner runner =
                new ToolCallingRunner(
                        model,
                        new ToolCallExecutor(dispatcher),
                        3,
                        Duration.ofSeconds(10)
                );

        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        AssistantService service =
                new AssistantService(
                        runner,
                        memory
                );

        ChatResponse response = service.ask(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "我的订单有哪些"
                )
        );

        assertEquals(
                "你目前没有订单",
                response.answer()
        );

        assertEquals(
                List.of(
                        new ChatMessage(
                                "user",
                                "我的订单有哪些",
                                1
                        ),
                        new ChatMessage(
                                "assistant",
                                "你目前没有订单",
                                2
                        )
                ),
                memory.load(
                        new ConversationKey(
                                "user-a",
                                "session-1"
                        )
                )
        );

        verify(
                dispatcher,
                times(1)
        ).dispatch(
                "list_my_orders",
                Map.of()
        );
    }
    @Test
    void failedFinalReplyShouldPreserveHistoryAfterToolRuns() {
        InMemoryConversationMemory memory = new InMemoryConversationMemory();
        ConversationKey key = new ConversationKey("1001", "d18-final-failure");

        memory.appendTurn(key, "旧问题", "旧回答");
        List<ChatMessage> before = memory.load(key);

        ToolDispatcher dispatcher = mock(ToolDispatcher.class);
        doReturn(ToolResult.success(List.of()))
                .when(dispatcher).dispatch("list_my_orders", Map.of());

        ChatModel model = mock(ChatModel.class);
        ModelTurn firstTurn = new ModelTurn(null, List.of(
                new ToolCall("call-d18", "list_my_orders", Map.of())));
        ModelCallException failure =
                new ModelCallException("模拟最终回答失败");

        when(model.nextTurn(any(ModelInput.class), any(TimeBudget.class)))
                .thenReturn(firstTurn)
                .thenThrow(failure);

        ToolCallingRunner runner = new ToolCallingRunner(
                model, new ToolCallExecutor(dispatcher),
                3, Duration.ofSeconds(10));
        AssistantService service = new AssistantService(
                runner, memory, new FakeCurrentUserContext(1001L));
        ChatRequest request =
                new ChatRequest("1001", "d18-final-failure", "查询我的订单");

        ModelCallException actual = assertThrows(
                ModelCallException.class,
                () -> service.ask(request)
        );
        assertSame(failure, actual);
        assertEquals(before,memory.load(key));
        verify(dispatcher,times(1)).dispatch("list_my_orders", Map.of());
        verify(model, times(2)).nextTurn(
                any(ModelInput.class),
                any(TimeBudget.class)
        );

        verifyNoMoreInteractions(model, dispatcher);
    }
}
