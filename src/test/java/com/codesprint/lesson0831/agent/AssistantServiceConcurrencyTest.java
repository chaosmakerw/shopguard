package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.product.ToolDispatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AssistantServiceConcurrencyTest {
    @Test
    void sameConversationShouldFinishFirstTurnBeforeLoadingSecondTurn() throws Exception {
        var firstModelStarted = new CountDownLatch(1);
        var allowFirstToFinish = new CountDownLatch(1);
        var secondRequestStarted = new CountDownLatch(1);
        var secondHistory = new AtomicReference<List<ChatMessage>>();
        ChatModel model = (request, history) -> {
            if (request.question().equals("first")) {
                firstModelStarted.countDown();
                await(allowFirstToFinish);
            } else {
                secondHistory.set(history);
            }
            return "answer:" + request.question();
        };
        var memory = new InMemoryConversationMemory();
        var service = service(model, memory);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ChatResponse> first = pool.submit(() -> service.ask(new ChatRequest("1001", "same", "first")));
            assertTrue(firstModelStarted.await(2, TimeUnit.SECONDS));
            Future<ChatResponse> second = pool.submit(() -> {
                secondRequestStarted.countDown();
                return service.ask(new ChatRequest("1001", "same", "second"));
            });
            assertTrue(secondRequestStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            allowFirstToFinish.countDown();
            assertEquals("answer:first", first.get(2, TimeUnit.SECONDS).answer());
            assertEquals("answer:second", second.get(2, TimeUnit.SECONDS).answer());
            assertEquals(List.of(new ChatMessage("user", "first", 1),
                    new ChatMessage("assistant", "answer:first", 2)), secondHistory.get());
            assertEquals(List.of(1L, 2L, 3L, 4L), memory.load(new ConversationKey("1001", "same"))
                    .stream().map(ChatMessage::sequence).toList());
        } finally {
            allowFirstToFinish.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void failureShouldReleaseConversationLockForAnotherThread() throws Exception {
        ChatModel model = (request, history) -> {
            if (request.question().equals("fail")) throw new ModelCallException("simulated failure");
            return "recovered";
        };
        var memory = new InMemoryConversationMemory();
        var service = service(model, memory);
        assertThrows(ModelCallException.class,
                () -> service.ask(new ChatRequest("1001", "retry", "fail")));
        assertTrue(memory.load(new ConversationKey("1001", "retry")).isEmpty());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertEquals("recovered", pool.submit(() -> service.ask(new ChatRequest("1001", "retry", "ok")))
                    .get(2, TimeUnit.SECONDS).answer());
            assertEquals(2, memory.load(new ConversationKey("1001", "retry")).size());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void overlongKeyShouldStopBeforeModelAndStorage() {
        var modelCalls = new AtomicInteger();
        ChatModel model = (request, history) -> { modelCalls.incrementAndGet(); return "unused"; };
        var memory = mock(ConversationMemory.class);
        var service = service(model, memory);
        assertEquals("会话标识不能超过100个字符", assertThrows(IllegalArgumentException.class,
                () -> service.ask(new ChatRequest("1001", "s".repeat(101), "q"))).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.ask(new ChatRequest("u".repeat(101), "s", "q")));
        assertEquals(0, modelCalls.get());
        org.mockito.Mockito.verifyNoInteractions(memory);
    }

    private AssistantService service(ChatModel model, ConversationMemory memory) {
        return new AssistantService(new ToolCallingRunner(model,
                new ToolCallExecutor(mock(ToolDispatcher.class)), 1, Duration.ofSeconds(5)), memory);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test state");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test interrupted", exception);
        }
    }
}
