package com.codesprint.lesson0831.agent;

import java.util.ArrayList;
import java.util.List;

public class ContextWindow {

    public List<ChatMessage> select(
            List<ChatMessage> messages,
            int maxRegularMessages
    ) {
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> regularMessages = new ArrayList<>();

        for (ChatMessage message : messages) {
            if ("system".equals(message.role())) {
                systemMessages.add(message);
            } else {
                regularMessages.add(message);
            }
        }
        int startIndex = Math.max(
                0,
                regularMessages.size() - maxRegularMessages
        );

        List<ChatMessage> recentRegularMessages =
                new ArrayList<>(
                        regularMessages.subList(
                                startIndex,
                                regularMessages.size()
                        )
                );
        List<ChatMessage> selected =
                new ArrayList<>(systemMessages);

        selected.addAll(recentRegularMessages);

        return selected;
    }
}