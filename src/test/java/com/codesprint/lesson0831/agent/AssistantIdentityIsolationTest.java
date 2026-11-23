package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;

class AssistantIdentityIsolationTest {

    @Test
    void service_should_use_trusted_context_instead_of_request_user_id() {
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();
        ToolCallingRunner runner = new ToolCallingRunner(
                (request, history) -> "ok",
                new ToolCallExecutor(mock(ToolDispatcher.class)),
                1,
                Duration.ofSeconds(1)
        );
        AssistantService service = new AssistantService(
                runner,
                memory,
                new FakeCurrentUserContext(1001L)
        );

        service.ask(new ChatRequest("attacker", "session-1", "hello"));

        assertEquals(
                2,
                memory.load(new ConversationKey("1001", "session-1")).size()
        );
        assertEquals(
                0,
                memory.load(new ConversationKey("attacker", "session-1")).size()
        );
    }

    @Test
    void service_should_reject_missing_trusted_identity() {
        AssistantService service = new AssistantService(
                new ToolCallingRunner(
                        (request, history) -> "ok",
                        new ToolCallExecutor(mock(ToolDispatcher.class)),
                        1,
                        Duration.ofSeconds(1)
                ),
                new InMemoryConversationMemory(),
                new FakeCurrentUserContext(null)
        );

        assertThrows(
                com.codesprint.lesson0831.common.BizException.class,
                () -> service.ask(new ChatRequest("attacker", "session-1", "hello"))
        );
    }
}
