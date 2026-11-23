package com.codesprint.lesson0831.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTurnTest {
    @Test
    void shouldKeepSnapshotAndContinueAfterHighestSequence() {
        var memory = new InMemoryConversationMemory();
        var key = new ConversationKey("user", "session");
        memory.append(key, new ChatMessage("system", "rules", 10));
        List<ChatMessage> snapshot = memory.load(key);
        memory.appendTurn(key, "q", "a");
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertEquals(List.of(10L, 11L, 12L), memory.load(key).stream().map(ChatMessage::sequence).toList());
    }

    @Test
    void concurrentWritersShouldPreserveWholeTurnsWithoutLostMessages() throws Exception {
        var memory = new InMemoryConversationMemory();
        var key = new ConversationKey("user", "session");
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int index = 0; index < 4; index++) {
                final int writer = index;
                tasks.add(pool.submit(() -> {
                    try {
                        if (!start.await(2, TimeUnit.SECONDS)) throw new AssertionError("Start state timeout");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    for (int turn = 0; turn < 25; turn++) {
                        memory.appendTurn(key, "q-" + writer + "-" + turn, "a-" + writer + "-" + turn);
                        assertEquals(0, memory.load(key).size() % 2);
                    }
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
            List<ChatMessage> messages = memory.load(key);
            assertEquals(200, messages.size());
            for (int index = 0; index < messages.size(); index += 2) {
                assertEquals(index + 1L, messages.get(index).sequence());
                assertEquals(index + 2L, messages.get(index + 1).sequence());
                assertEquals(messages.get(index).content().substring(2), messages.get(index + 1).content().substring(2));
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}
