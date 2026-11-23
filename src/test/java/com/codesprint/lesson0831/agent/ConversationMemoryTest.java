package com.codesprint.lesson0831.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationMemoryTest {

    @Test
    void should_isolate_messages_by_user_and_session() {
        InMemoryConversationMemory memory=new InMemoryConversationMemory();
        ConversationKey userA =new ConversationKey("user-a","session-1");
        ConversationKey userB =new ConversationKey("user-b","session-1");
        memory.append(userA,
                new ChatMessage("user", "question", 1));

        memory.append(userB,
                new ChatMessage("user", "answer", 1));
        memory.load(userA);
        memory.load(userB);
        assertEquals(1,memory.load(userA).size());
        assertEquals(1,memory.load(userB).size());
        assertEquals(
                "question",
                memory.load(userA).get(0).content()
        );
        assertEquals("answer",memory.load(userB).get(0).content());
    }
    @Test
    void should_keep_system_message_first_and_limit_regular_messages() {
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "系统规则：你是交易助手", 1),
                new ChatMessage("user", "问题 1：有哪些商品？", 2),
                new ChatMessage("assistant", "回答 1：有 Java 书和耳机", 3),
                new ChatMessage("user", "问题 2：Java 书多少钱？", 4),
                new ChatMessage("assistant", "回答 2：价格是 30 元", 5),
                new ChatMessage("user", "问题 3：有库存吗？", 6),
                new ChatMessage("assistant", "回答 3：还有库存", 7),
                new ChatMessage("user", "问题 4：可以包邮吗？", 8),
                new ChatMessage("assistant", "回答 4：可以包邮", 9)
        );
        ContextWindow window = new ContextWindow();

        List<ChatMessage> selected =
                window.select(messages, 4);
        assertEquals(5, selected.size());
        assertEquals("system", selected.get(0).role());
        assertEquals(
                "回答 3：还有库存",
                selected.get(2).content()
        );

        assertEquals(
                "问题 4：可以包邮吗？",
                selected.get(3).content()
        );

        assertEquals(
                "回答 4：可以包邮",
                selected.get(4).content()
        );
    }
    @Test
    void should_keep_all_messages_when_regular_messages_are_less_than_limit() {
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "系统规则", 1),
                new ChatMessage("user", "只有一个问题", 2),
                new ChatMessage("assistant", "只有一个回答", 3)
        );
        ContextWindow window =new ContextWindow();
        List<ChatMessage> selected = window.select(messages,4);
        assertEquals(3,selected.size());
        assertEquals("system",selected.get(0).role());
        assertEquals("只有一个问题",selected.get(1).content());
        assertEquals("只有一个回答",selected.get(2).content());
    }
    @Test
    void should_return_empty_when_messages_are_empty() {
        ContextWindow window = new ContextWindow();

        List<ChatMessage> selected =
                window.select(List.of(), 4);

        assertEquals(0, selected.size());
    }
    @Test
    void should_keep_system_only_when_regular_limit_is_zero() {
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "系统规则", 1),
                new ChatMessage("user", "普通问题", 2),
                new ChatMessage("assistant", "普通回答", 3)
        );

        ContextWindow window = new ContextWindow();

        List<ChatMessage> selected =
                window.select(messages, 0);

        assertEquals(1, selected.size());
        assertEquals("system", selected.get(0).role());
        assertEquals("系统规则", selected.get(0).content());
    }
}