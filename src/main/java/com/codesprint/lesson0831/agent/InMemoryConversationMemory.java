package com.codesprint.lesson0831.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component
@Profile("!prod")
public class InMemoryConversationMemory implements ConversationMemory{
    private final Map<ConversationKey,List<ChatMessage>> conversations = new HashMap<>();

    @Override
    public synchronized void append(ConversationKey key, ChatMessage message) {
        conversations.computeIfAbsent(
                key,
                ignored -> new ArrayList<>()).add(message);
    }

    @Override
    public synchronized List<ChatMessage> load(ConversationKey key) {
        return List.copyOf(conversations.getOrDefault(key,List.of()));
    }

    @Override
    public synchronized void appendTurn(ConversationKey key, String question, String answer) {
        ConversationMemory.super.appendTurn(key, question, answer);
    }
}
