package com.codesprint.lesson0831.agent;

import java.util.List;

public interface ConversationMemory {
    void append(ConversationKey key,ChatMessage message);
    List<ChatMessage> load(ConversationKey key);

    /** 兼容原有替身；正式存储实现应覆盖此方法，原子保存完整的一轮。 */
    default void appendTurn(ConversationKey key, String question, String answer) {
        if (question == null || answer == null) {
            throw new IllegalArgumentException("问答内容不能为空");
        }
        long sequence = Math.addExact(load(key).stream()
                .mapToLong(ChatMessage::sequence).max().orElse(0L), 1L);
        long assistantSequence = Math.addExact(sequence, 1L);
        append(key, new ChatMessage("user", question, sequence));
        append(key, new ChatMessage("assistant", answer, assistantSequence));
    }
}
